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

import org.opencastproject.fex.api.FexQuery;
import org.opencastproject.fex.objects.Fex;
import org.opencastproject.metadata.dublincore.DublinCoreCatalogList;
import org.opencastproject.util.NotFoundException;

import java.util.List;

public interface FexServiceIndex {
  /**
   * Performs any necessary setup and activates index.
   */
  void activate();

  /**
   * Deactivates index and performs any necessary cleanup
   */
  void deactivate();

  /**
   * Index organization for existing fex entry
   *
   * @param fexId        ID of fex
   * @param organization new organization for fex
   * @throws NotFoundException           if fex not exists
   * @throws FexServiceDatabaseException if exception occurs
   */
  void updateFexOrganization(String fexId, String organization) throws NotFoundException, FexServiceDatabaseException;

  /**
   * Index receiver for existing fex entry
   *
   * @param fexId    ID of fex
   * @param receiver new organization for fex
   * @throws NotFoundException           if fex not exists
   * @throws FexServiceDatabaseException if exception occurs
   */
  void updateFexReceiver(String fexId, String receiver) throws NotFoundException, FexServiceDatabaseException;

  /**
   * Index Lecture id for existing fex entry
   *
   * @param fexId     ID of fex
   * @param lectureId new lecture id of fex
   * @throws NotFoundException           if fex not exists
   * @throws FexServiceDatabaseException if exception occurs
   */
  void updateFexLectureId(String fexId, String lectureId) throws NotFoundException, FexServiceDatabaseException;

  /**
   * Index series id for existing fex entry
   *
   * @param fexId    ID of fex
   * @param seriesId new series id of fex
   * @throws NotFoundException           if fex not exists
   * @throws FexServiceDatabaseException if exception occurs
   */
  void updateFexSeriesId(String fexId, String seriesId) throws NotFoundException, FexServiceDatabaseException;

  /**
   * Index SBS-status for existing fex entry
   *
   * @param fexId ID of fex
   * @param sbs   new sbs-status of fex
   * @throws NotFoundException           if fex not exists
   * @throws FexServiceDatabaseException if exception occurs
   */
  void udateSbsStatus(String fexId, boolean sbs) throws NotFoundException, FexServiceDatabaseException;

  /**
   * Removes fex from index
   *
   * @param fexId ID of fex
   * @throws FexServiceDatabaseException if exception occurred
   */
  void delete(String fexId) throws FexServiceDatabaseException;

  /**
   * Gets organization of fex
   *
   * @param fexId ID of fex
   * @return the organization
   * @throws NotFoundException           if fex not exists
   * @throws FexServiceDatabaseException if exception occurs
   */
  String getFexOrganization(String fexId) throws NotFoundException, FexServiceDatabaseException;

  /**
   * Gets Receiver of fex
   *
   * @param fexId ID of fex
   * @return the Receiver
   * @throws NotFoundException           if fex not exists
   * @throws FexServiceDatabaseException if exception occurs
   */
  String getFexReceiver(String fexId) throws NotFoundException, FexServiceDatabaseException;

  /**
   * Gets lecture id of fex
   *
   * @param fexId ID of fex
   * @return the lecture id
   * @throws NotFoundException           if fex not exists
   * @throws FexServiceDatabaseException if exception occurs
   */
  String getFexLectureID(String fexId) throws NotFoundException, FexServiceDatabaseException;

  /**
   * Gets series of fex
   *
   * @param fexId ID of fex
   * @return the series
   * @throws NotFoundException           if fex not exists
   * @throws FexServiceDatabaseException if exception occurs
   */
  String getFexSeriesID(String fexId) throws NotFoundException, FexServiceDatabaseException;

  /**
   * Returns sbs status  of fex
   *
   * @param fexId ID of fex
   * @return the sbs status
   * @throws NotFoundException           if fex not exists
   * @throws FexServiceDatabaseException if exception occurs
   */
  boolean isSbs(String fexId) throws NotFoundException, FexServiceDatabaseException;

  /**
   * Search over indexed fex with query
   *
   * @param query FexQuery object sorting query parameters
   * @return List of all matching fex
   * @throws FexServiceDatabaseException
   */
  DublinCoreCatalogList search(FexQuery query) throws FexServiceDatabaseException;

  /**
   * Query Id of all fex
   *
   * @return list of fex Id of all fex
   * @throws FexServiceDatabaseException
   */
  List<String> queryIdTitleMap() throws FexServiceDatabaseException;

  /**
   * Returns number of fex in search index, across all organizations.
   *
   * @return number of fex in search index
   * @throws FexServiceDatabaseException if exception occurs
   */
  long count() throws FexServiceDatabaseException;

  Fex getFex(String fexId) throws NotFoundException, FexServiceDatabaseException;
}
