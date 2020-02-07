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
package org.opencastproject.workflow.handler;

import static org.opencastproject.workflow.handler.IliasPublicationChannel.CHANNEL_ID;

import org.opencastproject.distribution.api.DistributionService;
import org.opencastproject.job.api.Job;
import org.opencastproject.job.api.JobContext;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.Publication;
import org.opencastproject.workflow.api.AbstractWorkflowOperationHandler;
import org.opencastproject.workflow.api.WorkflowInstance;
import org.opencastproject.workflow.api.WorkflowOperationException;
import org.opencastproject.workflow.api.WorkflowOperationResult;
import org.opencastproject.workflow.api.WorkflowOperationResult.Action;

import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Workflow operation for retracting a media package from ilias.
 */
public class RetractIliasWorkflowOperationHandler extends AbstractWorkflowOperationHandler {

  /** The logging facility */
  private static final Logger logger = LoggerFactory.getLogger(RetractIliasWorkflowOperationHandler.class);

  /** The configuration options for this handler */
  private static final SortedMap<String, String> CONFIG_OPTIONS = new TreeMap<String, String>();

  /** The ilias distribution service */
  private DistributionService iliasDistributionService = null;

  /**
   * Callback for the OSGi declarative services configuration.
   *
   * @param iliasDistributionService
   *          the ilias distribution service
   */
  protected void setIliasDistributionService(DistributionService iliasDistributionService) {
    this.iliasDistributionService = iliasDistributionService;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.workflow.api.AbstractWorkflowOperationHandler#activate(org.osgi.service.component.ComponentContext)
   */
  @Override
  protected void activate(ComponentContext cc) {
    super.activate(cc);
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.workflow.api.WorkflowOperationHandler#start(WorkflowInstance, JobContext)
   */
  @Override
  public WorkflowOperationResult start(WorkflowInstance workflowInstance, JobContext context)
          throws WorkflowOperationException {
    MediaPackage mediaPackage = workflowInstance.getMediaPackage();
    try {
      List<Job> jobs = new ArrayList<Job>();

      // The iliasRetractJob can only delete entire mediaPackages. There is no need to retract individual elements
      logger.info("Retracting media package {} from download/streaming distribution channel", mediaPackage);
      Job retractIliasJob = iliasDistributionService.retract(CHANNEL_ID, mediaPackage, null);
      jobs.add(retractIliasJob);

      // Wait for retraction to finish
      if (!waitForStatus(jobs.toArray(new Job[jobs.size()])).isSuccess())
        throw new WorkflowOperationException("The Ilias retract job did not complete successfully");

      logger.debug("Retraction operation complete");

      // Remove publication element
      logger.info("Removing ilias publication element from media package {}", mediaPackage);
      Publication[] publications = mediaPackage.getPublications();
      for (Publication publication : publications) {
        if (CHANNEL_ID.equals(publication.getChannel())) {
          mediaPackage.remove(publication);
          logger.debug("Remove ilias publication element '{}' complete", publication);
        }
      }

      return createResult(mediaPackage, Action.CONTINUE);
    } catch (Throwable t) {
      throw new WorkflowOperationException(t);
    }
  }

}
