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

package org.opencastproject.workflow.handler.ingest;

import org.opencastproject.ingestdownloadservice.api.IngestDownloadService;
import org.opencastproject.job.api.Job;
import org.opencastproject.job.api.JobContext;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageException;
import org.opencastproject.mediapackage.MediaPackageParser;
import org.opencastproject.security.api.TrustedHttpClient;
import org.opencastproject.serviceregistry.api.ServiceRegistryException;
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

/**
 * Downloads all external URI's to the working file repository and optionally deletes external working file repository
 * resources
 */
public class IngestDownloadWorkflowOperationHandler extends AbstractWorkflowOperationHandler {

  /** The logger */
  private static final Logger logger = LoggerFactory.getLogger(IngestDownloadWorkflowOperationHandler.class);

  /** Deleting external working file repository URI's after download config key */
  public static final String DELETE_EXTERNAL = "delete-external";

  /** config key used to specify a list of flavors (seperated by comma), elements matching a flavor will be downloaded */
  public static final String SOURCE_FLAVORS = "source-flavors";

  /** config key used to specify a list of tags (seperated by comma), elements matching a tag will be downloaded */
  public static final String SOURCE_TAGS = "source-tags";

  /** config key used to specify, whether both, a tag and a flavor, must match or if one is sufficient */
  public static final String TAGS_AND_FLAVORS = "tags-and-flavors";

  /**
   * The workspace to use in retrieving and storing files.
   */
  protected Workspace workspace;

  /** The http client to use when connecting to remote servers */
  protected TrustedHttpClient client = null;
  protected IngestDownloadService ingestDownloadService;

  /** The default no-arg constructor builds the configuration options set */
  public IngestDownloadWorkflowOperationHandler() {
  }

  /**
   * Sets the workspace to use.
   *
   * @param workspace
   *          the workspace
   */
  public void setWorkspace(Workspace workspace) {
    this.workspace = workspace;
  }


  /**
   * Sets the trusted http client
   *
   * @param client
   *          the trusted http client
   */
  public void setTrustedHttpClient(TrustedHttpClient client) {
    this.client = client;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.workflow.api.AbstractWorkflowOperationHandler#start(org.opencastproject.workflow.api.WorkflowInstance,
   *      JobContext)
   */

  public void setIngestDownloadService(IngestDownloadService ingestDownloadService) {
    this.ingestDownloadService = ingestDownloadService;
  }

  @Override
  public WorkflowOperationResult start(WorkflowInstance workflowInstance, JobContext context)
          throws WorkflowOperationException {
    WorkflowOperationInstance currentOperation = workflowInstance.getCurrentOperation();

    boolean deleteExternal = BooleanUtils.toBoolean(currentOperation.getConfiguration(DELETE_EXTERNAL));
    boolean tagsAndFlavor = BooleanUtils.toBoolean(currentOperation.getConfiguration(TAGS_AND_FLAVORS));
    String sourceFlavors = getConfig(workflowInstance, SOURCE_FLAVORS, "*/*");
    String sourceTags = getConfig(workflowInstance, SOURCE_TAGS, "");
    WorkflowOperationResult result = null;
    try {
      Job job = ingestDownloadService
              .ingestDownload(workflowInstance.getMediaPackage(), sourceFlavors, sourceTags, deleteExternal, tagsAndFlavor);

      // Wait for all jobs to be finished
      if (!waitForStatus(job).isSuccess())
        throw new WorkflowOperationException("Execute operation failed");

      if (StringUtils.isNotBlank(job.getPayload())) {
        MediaPackage mediaPackage = MediaPackageParser.getFromXml(job.getPayload());
        result = createResult(mediaPackage, Action.CONTINUE, job.getQueueTime());
      }

    } catch (MediaPackageException e) {
      throw new WorkflowOperationException("Some result element couldn't be serialized", e);
    } catch (ServiceRegistryException e) {
      e.printStackTrace();

    }
    return result;
  }
}
