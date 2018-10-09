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

package org.opencastproject.index.service.message;

import static org.opencastproject.index.service.impl.index.event.EventIndexUtils.getOrCreateEvent;
import static org.opencastproject.index.service.impl.index.event.EventIndexUtils.updateEvent;

import org.opencastproject.authorization.xacml.manager.api.AclServiceFactory;
import org.opencastproject.authorization.xacml.manager.api.ManagedAcl;
import org.opencastproject.index.service.impl.index.event.Event;
import org.opencastproject.index.service.impl.index.event.EventIndexUtils;
import org.opencastproject.index.service.util.AccessInformationUtil;
import org.opencastproject.matterhorn.search.SearchIndexException;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.message.broker.api.MessageSender;
import org.opencastproject.message.broker.api.workflow.WorkflowItem;
import org.opencastproject.metadata.dublincore.DublinCoreCatalog;
import org.opencastproject.security.api.AccessControlParser;
import org.opencastproject.security.api.AccessControlParsingException;
import org.opencastproject.security.api.AuthorizationService;
import org.opencastproject.security.api.User;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.util.data.Option;
import org.opencastproject.workspace.api.Workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class WorkflowMessageReceiverImpl extends BaseMessageReceiverImpl<WorkflowItem> {

  private static final Logger logger = LoggerFactory.getLogger(WorkflowMessageReceiverImpl.class);

  private Workspace workspace;
  private AuthorizationService authorizationService;
  private AclServiceFactory aclServiceFactory;

  /**
   * Creates a new message receiver that is listening to the admin ui destination of the workflow queue.
   */
  public WorkflowMessageReceiverImpl() {
    super(MessageSender.DestinationType.Queue);
  }

  @Override
  protected void execute(WorkflowItem workflowItem) {
    logger.error("received workflow item of type '{}'", workflowItem.getType());
    long startTime = System.currentTimeMillis();
    switch (workflowItem.getType()) {
      case UpdateInstance:
        updateWorkflowInstance(workflowItem);
        break;
      case DeleteInstance:
        deleteWorkflowInstance(workflowItem);
        break;
      default:
        throw new IllegalArgumentException("Unhandled type of WorkflowItem");
    }
    long endTime = System.currentTimeMillis();
    logger.error("Processing time for received {} message: {}", workflowItem.getType(), endTime - startTime);
  }

  private void deleteWorkflowInstance(WorkflowItem workflowItem) {
    final String organization = getSecurityService().getOrganization().getId();
    final User user = getSecurityService().getUser();
    final String eventId = workflowItem.getId();
    logger.debug("Received Delete Workflow instance Entry {}", eventId);

    // Remove the Workflow instance entry from the search index
    try {
      getSearchIndex().deleteWorkflow(organization, user, eventId, workflowItem.getWorkflowInstanceId());
      logger.debug("Workflow instance media package {} removed from search index", eventId);
    } catch (NotFoundException e) {
      logger.warn("Workflow instance media package {} not found for deletion", eventId);
    } catch (SearchIndexException e) {
      logger.error("Error deleting the Workflow instance entry {} from the search index", eventId, e);
    }
  }

  private void updateWorkflowInstance(WorkflowItem workflowItem) {
    logger.debug("Received Update Workflow instance Entry for index {}", getSearchIndex().getIndexName());
    final String organization = getSecurityService().getOrganization().getId();
    final User user = getSecurityService().getUser();
    final MediaPackage mp = workflowItem.getMediaPackage();
    final String eventId = workflowItem.getId();

    // Load or create the corresponding recording event
    Event event;
    try {
      event = getOrCreateEvent(eventId, organization, user, getSearchIndex());
      event.setCreator(getSecurityService().getUser().getName());
      event.setWorkflowId(workflowItem.getWorkflowInstanceId());
      event.setWorkflowDefinitionId(workflowItem.getWorkflowDefinitionId());
      event.setWorkflowState(workflowItem.getState());

      if (!workflowItem.getState().isTerminated()) {
        String aclJSON = workflowItem.getAccessControlListJSON();
        List<ManagedAcl> acls = aclServiceFactory.serviceFor(getSecurityService().getOrganization()).getAcls();
        try {
          Option<ManagedAcl> managedAcl = AccessInformationUtil.matchAcls(acls, AccessControlParser.parseAcl(aclJSON));
          if (managedAcl.isSome()) {
            event.setManagedAcl(managedAcl.get().getName());
          }
        } catch (IOException | AccessControlParsingException e) {
          logger.error("Could not parse ACL", e);
        }

        event.setAccessPolicy(aclJSON);

        DublinCoreCatalog dcCatalog = workflowItem.getEpisodeDublincoreCatalog();
        if (dcCatalog != null) {
          updateEvent(event, dcCatalog);
        }
      }
      updateEvent(event, mp);
    } catch (SearchIndexException e) {
      logger.error("Error retrieving the recording event from the search index", e);
      return;
    }

    // Update series name if not already done
    try {
      EventIndexUtils.updateSeriesName(event, organization, user, getSearchIndex());
    } catch (SearchIndexException e) {
      logger.error("Error updating the series name of the event to index", e);
    }

    // Persist the scheduling event
    try {
      getSearchIndex().addOrUpdate(event);
      logger.debug("Workflow instance {} updated in the search index", eventId);
    } catch (SearchIndexException e) {
      logger.error("Error retrieving the recording event from the search index", e);
    }

  }

  /** OSGi DI. */
  public void setWorkspace(Workspace workspace) {
    this.workspace = workspace;
  }

  /** OSGi DI. */
  public void setAuthorizationService(AuthorizationService authorizationService) {
    this.authorizationService = authorizationService;
  }

  /** OSGi callback for acl services. */
  public void setAclServiceFactory(AclServiceFactory aclServiceFactory) {
    this.aclServiceFactory = aclServiceFactory;
  }

}
