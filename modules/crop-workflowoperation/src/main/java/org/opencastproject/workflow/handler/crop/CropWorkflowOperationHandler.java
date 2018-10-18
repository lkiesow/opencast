/**
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 *
 * The Apereo Foundation licenses this file to you under the Educational
 * Community License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License
 * at:
 *
 *   http://opensource.org/licenses/ecl2.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */
package org.opencastproject.workflow.handler.crop;

import org.opencastproject.crop.api.CropException;
import org.opencastproject.crop.api.CropService;
import org.opencastproject.job.api.Job;
import org.opencastproject.job.api.JobContext;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageElementFlavor;
import org.opencastproject.mediapackage.MediaPackageElementParser;
import org.opencastproject.mediapackage.MediaPackageElements;
import org.opencastproject.mediapackage.MediaPackageException;
import org.opencastproject.mediapackage.Track;
import org.opencastproject.mediapackage.identifier.IdBuilder;
import org.opencastproject.mediapackage.identifier.IdBuilderFactory;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.util.data.Option;
import org.opencastproject.workflow.api.AbstractWorkflowOperationHandler;
import org.opencastproject.workflow.api.WorkflowInstance;
import org.opencastproject.workflow.api.WorkflowOperationException;
import org.opencastproject.workflow.api.WorkflowOperationInstance;
import org.opencastproject.workflow.api.WorkflowOperationResult;
import org.opencastproject.workspace.api.Workspace;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The workflow definition will run recordings to crop them from 4:3 to 16:9.
 */
public class CropWorkflowOperationHandler extends AbstractWorkflowOperationHandler {

  /** The logging facility */
  private static final Logger logger = LoggerFactory.getLogger(CropWorkflowOperationHandler.class);

  /** Name of the configuration key that specifies the flavor of the track to analyze */
  private static final String PROP_ANALYSIS_TRACK_FLAVOR = "source-flavor";

  private static final String PROP_TARGET_FLAVOR = "target-flavor";

  /** Name of the configuration key that specifies the flavor of the track to analyze */
  private static final String PROP_TARGET_TAGS = "target-tags";

  /** Id builder used to create ids for cropped tracks */
  private final IdBuilder idBuilder = IdBuilderFactory.newInstance().newIdBuilder();

  /** The composer service */
  private CropService cropService = null;

  /** The local workspace */
  private Workspace workspace = null;

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.workflow.api.WorkflowOperationHandler#start(org.opencastproject.workflow.api.WorkflowInstance, JobContext)
   */
  @Override
  public WorkflowOperationResult start(WorkflowInstance workflowInstance, JobContext jobContext)
          throws WorkflowOperationException {
    logger.debug("Running cropping on workflow {}", workflowInstance.getId());

    WorkflowOperationInstance operation = workflowInstance.getCurrentOperation();
    MediaPackage mediaPackage = workflowInstance.getMediaPackage();

    String trackFlavor = StringUtils.trimToNull(operation.getConfiguration(PROP_ANALYSIS_TRACK_FLAVOR));
    List<String> targetTags = asList(operation.getConfiguration(PROP_TARGET_TAGS));
    Option<MediaPackageElementFlavor> targetFlavor = getCfg(workflowInstance, PROP_TARGET_FLAVOR).map(
            MediaPackageElementFlavor.parseFlavor
    );
    List<Track> candidates = new ArrayList<>();
    if (trackFlavor != null) {
      candidates.addAll(Arrays.asList(mediaPackage.getTracks(MediaPackageElementFlavor.parseFlavor(trackFlavor))));
    } else {
      candidates.addAll(Arrays.asList(mediaPackage.getTracks(MediaPackageElements.PRESENTATION_SOURCE)));
    }
    candidates.removeIf(t -> !t.hasVideo());

    if (candidates.size() == 0) {
      logger.info("No matching tracks available for cropping in workflow {}", workflowInstance);
      return createResult(WorkflowOperationResult.Action.CONTINUE);
    }

    if (candidates.size() > 1) {
      logger.info("Found more than one track to crop");
    }

    // start cropping all candidates in parallel
    List<Job> jobs = new ArrayList<>();
    for (Track candidate: candidates) {
      try {
        jobs.add(cropService.crop(candidate));
      } catch (MediaPackageException | CropException e) {
        throw new WorkflowOperationException("Failed starting crop job", e);
      }
    }

    // wait for all crop jobs to be finished
    if (!waitForStatus(jobs.toArray(new Job[0])).isSuccess()) {
      throw new WorkflowOperationException("Crop operation failed");
    }

    long totalTimeInQueue = 0;

    // add new tracks to media package
    for (Job job: jobs) {
      // deserialize track
      Track croppedTrack;
      try {
        croppedTrack = (Track) MediaPackageElementParser.getFromXml(job.getPayload());
      } catch (MediaPackageException e) {
        throw new WorkflowOperationException(String.format("Crop service yielded invalid track: %s", job.getPayload()));
      }

      // update identifier
      croppedTrack.setIdentifier(idBuilder.createNew().toString());

      // move into space for media package in ws/wfr
      try {
        String filename = "cropped_" + croppedTrack.getURI().toString();
        croppedTrack.setURI(workspace.moveTo(croppedTrack.getURI(), mediaPackage.getIdentifier().toString(),
                croppedTrack.getIdentifier(), filename));
      } catch (NotFoundException | IOException e) {
        throw new WorkflowOperationException(String.format("Could not move %s to media package %s",
                croppedTrack.getURI(), mediaPackage.getIdentifier()));
      }

      // Add target tags
      targetTags.forEach(croppedTrack::addTag);

      for (MediaPackageElementFlavor flavor : targetFlavor) {
        croppedTrack.setFlavor(flavor);
      }

      // add new track to mediapackage
      mediaPackage.add(croppedTrack);

      totalTimeInQueue += job.getQueueTime() == null ? 0 : job.getQueueTime();
    }
    logger.info("Video cropping completed");
    return createResult(mediaPackage, WorkflowOperationResult.Action.CONTINUE, totalTimeInQueue);
  }

  /**
   * Callback for declarative services configuration that will introduce us to the crop service.
   * Implementation assumes that the reference is configured as being static.
   *
   * @param cropService
   *          the crop service
   */
  protected void setCropService(CropService cropService) {
    this.cropService = cropService;
  }

  /**
   * Callback for declarative services configuration that will introduce us to the local workspace service.
   * Implementation assumes that the reference is configured as being static.
   *
   * @param workspace
   *          an instance of the workspace
   */
  protected void setWorkspace(Workspace workspace) {
    this.workspace = workspace;
  }
}
