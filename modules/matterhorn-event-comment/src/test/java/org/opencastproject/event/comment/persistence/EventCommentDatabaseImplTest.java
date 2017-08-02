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

package org.opencastproject.event.comment.persistence;

import static org.junit.Assert.assertEquals;
import static org.opencastproject.event.comment.persistence.EventCommentDatabaseServiceImpl.PERSISTENCE_UNIT;
import static org.opencastproject.util.data.Option.none;
import static org.opencastproject.util.persistence.PersistenceUtil.newTestEntityManagerFactory;

import org.opencastproject.event.comment.EventComment;
import org.opencastproject.event.comment.EventCommentReply;
import org.opencastproject.message.broker.api.MessageSender;
import org.opencastproject.security.api.DefaultOrganization;
import org.opencastproject.security.api.JaxbUser;
import org.opencastproject.security.api.Organization;
import org.opencastproject.security.api.SecurityService;
import org.opencastproject.security.api.User;
import org.opencastproject.security.api.UserDirectoryService;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

public class EventCommentDatabaseImplTest {

  private static final Organization ORGANIZATION = new DefaultOrganization();
  private static final User USER = new JaxbUser("wilfried.meyer", "matterhorn", new DefaultOrganization());

  private static final String EVENT_1_ID = "1";
  private static final EventComment COMMENT_1 = EventComment.create(none(Long.class), EVENT_1_ID, ORGANIZATION.getId(), "test", USER);

  private EventCommentDatabaseServiceImpl persistence;

  @Before
  public void setUp() throws Exception {
    UserDirectoryService userDirectoryService = EasyMock.createNiceMock(UserDirectoryService.class);
    EasyMock.expect(userDirectoryService.loadUser(EasyMock.anyObject(String.class))).andReturn(USER).anyTimes();
    EasyMock.replay(userDirectoryService);

    SecurityService securityService = EasyMock.createNiceMock(SecurityService.class);
    EasyMock.expect(securityService.getOrganization()).andReturn(new DefaultOrganization()).anyTimes();
    EasyMock.replay(securityService);

    MessageSender messageSender = EasyMock.createNiceMock(MessageSender.class);
    EasyMock.replay(messageSender);

    persistence = new EventCommentDatabaseServiceImpl();
    persistence.setEntityManagerFactory(newTestEntityManagerFactory(PERSISTENCE_UNIT));
    persistence.setUserDirectoryService(userDirectoryService);
    persistence.setMessageSender(messageSender);
    persistence.setSecurityService(securityService);
    persistence.activate(null);
  }

  @Test
  public void testAddNewComment() throws Exception {
    final EventComment persistedComment = persistence.updateComment(COMMENT_1);

    assertEquals(COMMENT_1, persistence.getComment(persistedComment.getId().get()));
  }

  @Test
  public void testUpdateCommentReplies() throws Exception {
    final EventComment persistedComment = persistence.updateComment(COMMENT_1);
    assertEquals(COMMENT_1, persistedComment);

    persistedComment.addReply(EventCommentReply.create(none(Long.class), "comment1", USER));
    persistedComment.addReply(EventCommentReply.create(none(Long.class), "comment2", USER));

    final EventComment updatedComment = persistence.updateComment(persistedComment);
    updatedComment.removeReply(updatedComment.getReplies().get(0));
    updatedComment.addReply(EventCommentReply.create(none(Long.class), "comment3", USER));

    final EventComment modifiedComment = persistence.updateComment(updatedComment);
    assertEquals(2, modifiedComment.getReplies().size());
    assertEquals("comment2", modifiedComment.getReplies().get(0).getText());
    assertEquals("comment3", modifiedComment.getReplies().get(1).getText());
  }

}
