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
package org.opencastproject.workflow.handler.fex;

import org.opencastproject.fex.api.FexSendService;
import org.opencastproject.fex.api.FexService;
import org.opencastproject.fex.objects.Fex;
import org.opencastproject.job.api.Job;
import org.opencastproject.job.api.JobContext;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageElementFlavor;
import org.opencastproject.mediapackage.MediaPackageElements;
import org.opencastproject.mediapackage.Track;
import org.opencastproject.series.api.SeriesService;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.workflow.api.AbstractWorkflowOperationHandler;
import org.opencastproject.workflow.api.WorkflowInstance;
import org.opencastproject.workflow.api.WorkflowOperationException;
import org.opencastproject.workflow.api.WorkflowOperationInstance;
import org.opencastproject.workflow.api.WorkflowOperationResult;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

public class FexServiceWorkflowOperationHandler extends AbstractWorkflowOperationHandler {

  /**
   * The logging facility
   **/
  private static final Logger logger = LoggerFactory.getLogger(FexServiceWorkflowOperationHandler.class);
  /**
   * Name of the configuration key that specifies the flavor of the track to be send
   */
  private static final String PROP_ANALYSIS_TRACK_FLAVOR = "source-flavor";
  /**
   * Name of the configuration key that specifies the flavor of the track to be send
   */
  private static final String PROP_TARGET_TAGS = "target-tags";
  /**
   * The configuration options for this handler
   */
  private static final SortedMap<String, String> CONFIG_OPTIONS;

  static {
    CONFIG_OPTIONS = new TreeMap<String, String>();
    CONFIG_OPTIONS.put(PROP_ANALYSIS_TRACK_FLAVOR,
            "The flavor of the track to be send. If multiple tracks match this flavor, the first will be used.");
    CONFIG_OPTIONS.put(PROP_TARGET_TAGS, "The tags to apply to the resulting fex send");
  }

  /**
   * The Send service
   */
  private FexSendService fexSendService = null;
  /**
   * The series service
   */
  private SeriesService seriesService = null;
  /**
   * The fex service
   */
  private FexService fexService = null;

  /**
   * @return
   */
  public SortedMap<String, String> getConfigurationOptions() {
    return CONFIG_OPTIONS;
  }

  /**
   * @param workflowInstance
   * @param jobContext
   * @return
   * @throws WorkflowOperationException
   * @see org.opencastproject.workflow.handler.fex.FexServiceWorkflowOperationHandler
   */
  @Override
  public WorkflowOperationResult start(WorkflowInstance workflowInstance, JobContext jobContext)
          throws WorkflowOperationException {
    logger.info("Running fex send on Workflow {}", workflowInstance.getId());


    WorkflowOperationInstance operation = workflowInstance.getCurrentOperation();
    MediaPackage mediapackage = workflowInstance.getMediaPackage();
    String seriesId = null;

    String trackFlavor = StringUtils.trimToNull(operation.getConfiguration(PROP_ANALYSIS_TRACK_FLAVOR));
    List<String> targetTags = asList(operation.getConfiguration(PROP_TARGET_TAGS));
    List<Track> candidates = new ArrayList<>();
    if (trackFlavor != null) {
      candidates.addAll(Arrays.asList(mediapackage.getTracks(MediaPackageElementFlavor.parseFlavor(trackFlavor))));
    } else {
      candidates.addAll(Arrays.asList(mediapackage.getTracks(MediaPackageElements.PRESENTATION_SOURCE)));
    }

    if (candidates.size() == 0) {
      logger.info("No matching tracks available for fex send in workflow {}", workflowInstance);
      return createResult(WorkflowOperationResult.Action.CONTINUE);
    }

    Fex fex = null;
    try {
      seriesId = mediapackage.getSeries();
      fex = fexService.getFexBySeriesId(seriesId);
    } catch (NotFoundException e) {
      logger.info("Continue, because no Fex with given seriesId");
      return createResult(WorkflowOperationResult.Action.CONTINUE);
    } catch (Exception e) {
      logger.error("Exception during workflow operation of fex service occurred: {}", e.getStackTrace());
      throw new WorkflowOperationException(e);
    }

    Job job = null;
    logger.info("vor dem try-Block");
    try {
      job = fexSendService.fexSend(Arrays.asList(mediapackage.getTracks()), fex);
      logger.info("Inhalt von job: " + job);
    } catch (Exception e) {
      logger.info("Catchblock EXCEPTION");
      throw new WorkflowOperationException(e);
    }

    MediaPackage mediaPackage = workflowInstance.getMediaPackage();
    logger.info("fex send completed");
    return createResult(mediaPackage, WorkflowOperationResult.Action.CONTINUE, job.getQueueTime());

  }

  /**
   * Callback for declarative services configuration that will introduce us to the fex send service,
   * Implementation assumes that the reference is configured as being static.
   *
   * @param fexSendService
   */
  protected void setFexSendService(FexSendService fexSendService) {
    logger.info("Fexsend WOH: set fex send service ");
    this.fexSendService = fexSendService;
  }

  /**
   * Callback for declarative services configuration that will introduce us to the series service.
   * Implementation assumes that the reference is configured as being static.
   *
   * @param seriesService
   */
  protected void setSeriesService(SeriesService seriesService) {
    logger.info("Fexsend WOH: set series service ");
    this.seriesService = seriesService;
  }

  /**
   * Callback for declarative services configuration that will intorduce us to the fex service.
   * Implementation assumes that the reference is configured as being static.
   *
   * @param fexService
   */
  protected void setFexService(FexService fexService) {
    logger.info("Fexsend WOH: set fex service");
    this.fexService = fexService;
  }

}
