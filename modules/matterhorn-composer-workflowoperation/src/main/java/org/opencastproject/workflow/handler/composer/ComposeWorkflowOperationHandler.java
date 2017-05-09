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

package org.opencastproject.workflow.handler.composer;

import org.opencastproject.composer.api.ComposerService;
import org.opencastproject.composer.api.EncoderException;
import org.opencastproject.composer.api.EncodingProfile;
import org.opencastproject.job.api.Job;
import org.opencastproject.job.api.JobContext;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageElementFlavor;
import org.opencastproject.mediapackage.MediaPackageElementParser;
import org.opencastproject.mediapackage.MediaPackageException;
import org.opencastproject.mediapackage.Track;
import org.opencastproject.mediapackage.selector.AbstractMediaPackageElementSelector;
import org.opencastproject.mediapackage.selector.TrackSelector;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.workflow.api.AbstractWorkflowOperationHandler;
import org.opencastproject.workflow.api.WorkflowInstance;
import org.opencastproject.workflow.api.WorkflowOperationException;
import org.opencastproject.workflow.api.WorkflowOperationInstance;
import org.opencastproject.workflow.api.WorkflowOperationResult;
import org.opencastproject.workflow.api.WorkflowOperationResult.Action;
import org.opencastproject.workspace.api.Workspace;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The workflow definition for handling "compose" operations
 */
public class ComposeWorkflowOperationHandler extends AbstractWorkflowOperationHandler {

  /** The logging facility */
  private static final Logger logger = LoggerFactory.getLogger(ComposeWorkflowOperationHandler.class);

  /* The configuration options for this handler */

  /** Comma separated list of flavors identifying tracks to use as a source input */
  private static final String OPT_SOURCE_FLAVORS = "source-flavors";

  /** Comma separated list of tags identifying tracks to use as a source input */
  private static final String OPT_SOURCE_TAGS = "source-tags";

  /** The encoding profile(s) to use */
  private static final String OPT_ENCODING_PROFILES = "encoding-profiles";

  /** Flavor to apply to the encoded file */
  private static final String OPT_TARGET_FLAVOR = "target-flavor";

  /** The tags to apply to the encoded file */
  private static final String OPT_TARGET_TAGS = "target-tags";

  /** Specify if selected tracks are the intersection of those selected by tags and flavors */
  private static final String OPT_TAGS_AND_FLAVORS = "tags-and-flavors";

  /** The composer service */
  private ComposerService composerService = null;

  /** The local workspace */
  private Workspace workspace = null;

  /**
   * Callback for the OSGi declarative services configuration.
   *
   * @param composerService
   *          the local composer service
   */
  protected void setComposerService(ComposerService composerService) {
    this.composerService = composerService;
  }

  /**
   * Callback for declarative services configuration that will introduce us to the local workspace service.
   * Implementation assumes that the reference is configured as being static.
   *
   * @param workspace
   *          an instance of the workspace
   */
  public void setWorkspace(Workspace workspace) {
    this.workspace = workspace;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.workflow.api.WorkflowOperationHandler#start(org.opencastproject.workflow.api.WorkflowInstance,
   *      JobContext)
   */
  public WorkflowOperationResult start(final WorkflowInstance workflowInstance, JobContext context)
          throws WorkflowOperationException {
    logger.debug("Running compose workflow operation on workflow {}", workflowInstance.getId());

    try {
      return encode(workflowInstance.getMediaPackage(), workflowInstance.getCurrentOperation());
    } catch (Exception e) {
      throw new WorkflowOperationException(e);
    }
  }

  /**
   * Encode tracks from MediaPackage using profiles stored in properties and updates current MediaPackage.
   *
   * @param src
   *          The source media package
   * @param operation
   *          the current workflow operation
   * @return the operation result containing the updated media package
   * @throws EncoderException
   *           if encoding fails
   * @throws WorkflowOperationException
   *           if errors occur during processing
   * @throws IOException
   *           if the workspace operations fail
   * @throws NotFoundException
   *           if the workspace doesn't contain the requested file
   */
  private WorkflowOperationResult encode(MediaPackage src, WorkflowOperationInstance operation)
          throws EncoderException, IOException, NotFoundException, MediaPackageException, WorkflowOperationException {
    MediaPackage mediaPackage = (MediaPackage) src.clone();

    // Check which tags have been configured
    final String sourceTagsCfg = operation.getConfiguration(OPT_SOURCE_TAGS);
    final String targetTagsCfg = operation.getConfiguration(OPT_TARGET_TAGS);
    final String sourceFlavorsCfg = operation.getConfiguration(OPT_SOURCE_FLAVORS);
    final String targetFlavorCfg = StringUtils.trimToNull(operation.getConfiguration(OPT_TARGET_FLAVOR));
    final boolean tagsAndFlavorsCfg = BooleanUtils.toBoolean(operation.getConfiguration(OPT_TAGS_AND_FLAVORS));
    final String encodingProfilesCfg = operation.getConfiguration(OPT_ENCODING_PROFILES);

    AbstractMediaPackageElementSelector<Track> elementSelector = new TrackSelector();

    // Make sure either one of tags or flavors are provided
    if (StringUtils.isBlank(sourceTagsCfg) && StringUtils.isBlank(sourceFlavorsCfg)) {
      throw new WorkflowOperationException("No source tags or flavors specified");
    }

    // Select source flavors
    asList(sourceFlavorsCfg).forEach(elementSelector::addFlavor);

    // Select the source tags
    asList(sourceTagsCfg).forEach(elementSelector::addTag);

    // Find the encoding profile
    List<EncodingProfile> encodingProfiles = new ArrayList<>();
    for (String profileId: asList(encodingProfilesCfg)) {
      EncodingProfile profile =  composerService.getProfile(profileId);
      if (profile == null) {
        throw new WorkflowOperationException("Encoding profile '" + profileId + "' was not found");
      }
      encodingProfiles.add(profile);
    }
    // Make sure there is at least one profile
    if (encodingProfiles.isEmpty()) {
      throw new WorkflowOperationException("No encoding profile was specified");
    }

    // Target tags
    List<String> targetTags = asList(targetTagsCfg);

    // Target flavor
    MediaPackageElementFlavor targetFlavor = null;
    if (StringUtils.isNotBlank(targetFlavorCfg)) {
      targetFlavor = MediaPackageElementFlavor.parseFlavor(targetFlavorCfg);
    }

    // Look for matching tracks
    Collection<Track> elements = elementSelector.select(mediaPackage, tagsAndFlavorsCfg);
    if (elements.isEmpty()) {
      logger.info("No matching tracks found");
      return createResult(mediaPackage, Action.CONTINUE);
    }

    // Encode all tracks found
    long totalTimeInQueue = 0;
    Map<Job, Track> encodingJobs = new HashMap<>();
    for (Track track : elements) {
      // Encode the track with all profiles
      for (EncodingProfile profile : encodingProfiles) {
        logger.info("Encoding track {} using encoding profile '{}'", track, profile);
        encodingJobs.put(composerService.encode(track, profile.getIdentifier()), track);
      }
    }

    // Wait for the jobs to return
    if (!waitForStatus(encodingJobs.keySet().toArray(new Job[encodingJobs.size()])).isSuccess()) {
      throw new WorkflowOperationException("One of the encoding jobs did not complete successfully");
    }

    // Process the result
    for (Map.Entry<Job, Track> entry : encodingJobs.entrySet()) {
      Job job = entry.getKey();
      Track track = entry.getValue();

      // add this receipt's queue time to the total
      totalTimeInQueue += job.getQueueTime();
      // it is allowed for compose jobs to return an empty payload. See the EncodeEngine interface
      if (job.getPayload().length() > 0) {
        Track composedTrack = (Track) MediaPackageElementParser.getFromXml(job.getPayload());

        // Adjust the target tags
        for (String tag : targetTags) {
          logger.trace("Tagging composed track with '{}'", tag);
          composedTrack.addTag(tag);
        }

        // Adjust the target flavor. Make sure to account for partial updates
        if (targetFlavor != null) {
          String flavorType = targetFlavor.getType();
          String flavorSubtype = targetFlavor.getSubtype();
          if ("*".equals(flavorType))
            flavorType = track.getFlavor().getType();
          if ("*".equals(flavorSubtype))
            flavorSubtype = track.getFlavor().getSubtype();
          composedTrack.setFlavor(new MediaPackageElementFlavor(flavorType, flavorSubtype));
          logger.debug("Composed track has flavor '{}'", composedTrack.getFlavor());
        }

        // store new tracks to mediaPackage
        mediaPackage.addDerived(composedTrack, track);
        String fileName = getFileNameFromElements(track, composedTrack);
        composedTrack.setURI(workspace.moveTo(composedTrack.getURI(), mediaPackage.getIdentifier().toString(),
                                              composedTrack.getIdentifier(), fileName));
      }
    }

    logger.debug("Compose operation completed");
    return createResult(mediaPackage, Action.CONTINUE, totalTimeInQueue);
  }
}
