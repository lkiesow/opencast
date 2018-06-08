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
package org.opencastproject.fex.impl;

import org.opencastproject.fex.api.FexException;
import org.opencastproject.fex.api.FexService;
import org.opencastproject.fex.objects.Fex;
import org.opencastproject.message.broker.api.MessageReceiver;
import org.opencastproject.message.broker.api.MessageSender;
import org.opencastproject.security.api.OrganizationDirectoryService;
import org.opencastproject.security.api.SecurityService;
import org.opencastproject.security.util.SecurityUtil;
import org.opencastproject.util.NotFoundException;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class FexServiceImpl implements FexService {

  /**
   * Logging utility
   */
  private static final Logger logger = LoggerFactory.getLogger(FexServiceImpl.class);

  /**
   * Persistent storage
   */
  protected FexServiceDatabase persistence;

  /**
   * The security service
   */
  protected SecurityService securityService;

  /**
   * The organization directory
   */
  protected OrganizationDirectoryService orgDirectory;

  /**
   * The message broker service sender
   */
  protected MessageSender messageSender;

  /**
   * The message broker service receiver
   */
  protected MessageReceiver messageReceiver;

  /**
   * The systems user name
   */
  private String systemUserName;

  private static Error rethrow(Exception e) throws FexException {
    throw new FexException(e);
  }

  /**
   * OSGi callback for setting persistence.
   */
  public void setPersistence(FexServiceDatabase persistence) {
    this.persistence = persistence;
  }

  /**
   * OSGi callback for setting the organization directory service
   */
  public void setOrgDirectory(OrganizationDirectoryService orgDirectory) {
    this.orgDirectory = orgDirectory;
  }

  /**
   * OSGi callback for setting the message sender.
   */
  public void setMessageSender(MessageSender messageSender) {
    this.messageSender = messageSender;
  }

  /**
   * OSGi callback for setting the message receiver.
   */
  public void setMessageReceiver(MessageReceiver messageReceiver) {
    this.messageReceiver = messageReceiver;
  }

  /**
   * Activates Fex Service
   *
   * @param cc
   * @throws Exception
   */
  public void activate(ComponentContext cc) throws Exception {
    logger.info("Activating Fex Service");
    systemUserName = cc.getBundleContext().getProperty(SecurityUtil.PROPERTY_KEY_SYS_USER);

  }

  @Override
  public void deleteFex(String fexID) throws NotFoundException, FexException {
    try {
      this.persistence.deleteFex(fexID);
    } catch (FexServiceDatabaseException e1) {
      logger.error("Could not delete fex with id {} from persistence storage", fexID);
      throw new FexException(e1);
    }

  }

  @Override
  public List<Fex> getAllFex() throws FexException {
    try {
      return persistence.getAllFex();
    } catch (FexServiceDatabaseException e) {
      logger.error("Error occurred while getting fex: {}", ExceptionUtils.getStackTrace(e));
      throw new FexException(e);
    }
  }

  @Override
  public Fex getFex(String fexId) throws NotFoundException, FexException {
    try {
      return persistence.getFex(fexId).getFex();
    } catch (FexServiceDatabaseException e) {
      logger.error("Exception occurred while getting fex {}: {}", fexId, ExceptionUtils.getStackTrace(e));
      throw new FexException(e);
    }
  }

  @Override
  public Fex getFexBySeriesId(String seriesId) throws NotFoundException, FexException {
    try {
      return persistence.getFexBySeriesId(seriesId).getFex();
    } catch (FexServiceDatabaseException e) {
      logger.error("Exception occurred while getting fex by series id.", e);
      throw new FexException(e);
    }
  }

  @Override
  public int countFex() throws FexException {
    try {
      return persistence.countFex();
    } catch (FexServiceDatabaseException e) {
      logger.error("Exception occurred while counting fex.", e);
      throw new FexException(e);
    }
  }

  @Override
  public String getFexReceiver(String fexId) throws NotFoundException, FexException {
    try {
      return persistence.getFexReceiver(fexId);
    } catch (FexServiceDatabaseException e) {
      logger.error("Exception occurred while getting receiver of fex {}: {}", fexId, ExceptionUtils.getStackTrace(e));
      throw new FexException(e);
    }
  }

  @Override
  public String getLectureId(String fexId) throws NotFoundException, FexException {
    try {
      return persistence.getFexLectureId(fexId);
    } catch (FexServiceDatabaseException e) {
      logger.error("Exception occurred while getting lecture id of fex {}: {}", fexId, ExceptionUtils.getStackTrace(e));
      throw new FexException(e);
    }
  }

  @Override
  public String getFexSeriesId(String fexId) throws NotFoundException, FexException {
    try {
      return persistence.getFexSeriesId(fexId);
    } catch (FexServiceDatabaseException e) {
      logger.error("Exception occurred while getting Series id of fex {}: {}", fexId, ExceptionUtils.getStackTrace(e));
      throw new FexException(e);
    }
  }

  @Override
  public boolean isSbs(String fexId) throws NotFoundException, FexException {
    try {
      return persistence.isSbs(fexId);
    } catch (FexServiceDatabaseException e) {
      logger.error("Exception occurred while getting Sbs status of fex {}: {}", fexId, ExceptionUtils.getStackTrace(e));
      throw new FexException(e);
    }
  }

  @Override
  public void updateIsSbsStatus(String fexId, boolean sbs) throws NotFoundException, FexException {
    try {
      persistence.updateSbs(fexId, sbs);
    } catch (FexServiceDatabaseException e) {
      logger.error("Failed to update sbs status of fex {}: {}", fexId, ExceptionUtils.getStackTrace(e));
      throw new FexException(e);
    }
  }

  @Override
  public void updateReceiver(String fexId, String receiver) throws NotFoundException, FexException {
    try {
      persistence.updateReceiver(fexId, receiver);
    } catch (FexServiceDatabaseException e) {
      logger.error("Failed to update receiver of fex {}: {}", fexId, ExceptionUtils.getStackTrace(e));
      throw new FexException(e);
    }

  }

  @Override
  public void updateLectureId(String fexId, String lectureId) throws NotFoundException, FexException {
    try {
      persistence.updateLectureId(fexId, lectureId);
    } catch (FexServiceDatabaseException e) {
      logger.error("failed to update lecture id of fex {}: {}", fexId, ExceptionUtils.getStackTrace(e));
      throw new FexException(e);
    }

  }

  @Override
  public void updateSeriesId(String fexId, String seriesId) throws NotFoundException, FexException {
    try {
      persistence.updateSeriesId(fexId, seriesId);
    } catch (FexServiceDatabaseException e) {
      logger.error("Failed to update series id of fex {}: {}", fexId, ExceptionUtils.getStackTrace(e));
      throw new FexException(e);
    }

  }

  @Override
  public void addFex(String fex) throws FexException {
    try {
      persistence.storeFex(fex);
    } catch (FexServiceDatabaseException e) {
      logger.error("Failed to store fex {} : {}", fex, ExceptionUtils.getStackTrace(e));
      throw new FexException(e);
    }
  }

}
