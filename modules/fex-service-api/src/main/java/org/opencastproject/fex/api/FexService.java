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
package org.opencastproject.fex.api;

import org.opencastproject.fex.objects.Fex;
import org.opencastproject.util.NotFoundException;

import java.util.List;

/**
 * Created by ac129583 on 8/18/17.
 */
public interface FexService {
  /**
   * Indentifier for service registration and location
   */
  String JOB_TYPE = "org.opencastproject.fex";

  /**
   * Removes fex
   *
   * @param fexID ID of fex to be removed
   * @throws NotFoundException if fex not exists
   * @throws FexException      if deleting fails
   */
  void deleteFex(String fexID) throws NotFoundException, FexException;

  /**
   * Returns all stored fex
   *
   * @return array representing stored fex
   * @throws FexException if exception occurs
   */
  List<Fex> getAllFex() throws FexException;

  /**
   * gets fex by its id
   *
   * @param fexID id of fex
   * @return fex
   * @throws NotFoundException if not exists
   * @throws FexException      if exception occurs
   */
  Fex getFex(String fexID) throws NotFoundException, FexException;

  /**
   * counts number of fex
   *
   * @return Number of fex as Integer
   * @throws FexException
   */
  int countFex() throws FexException;

  /**
   * Gets Receiver of a fex
   *
   * @param fexId ID of fex
   * @return the receiver of fex
   * @throws NotFoundException if fex not exists
   * @throws FexException      if exception occurs
   */
  String getFexReceiver(String fexId) throws NotFoundException, FexException;

  /**
   * Gets lecture id of a fex
   *
   * @param fexId ID of fex
   * @return The lecture id of fex
   * @throws NotFoundException if fex not exists
   * @throws FexException      if exception occurs
   */
  String getLectureId(String fexId) throws NotFoundException, FexException;

  /**
   * Gets series id of a fex
   *
   * @param fexId ID of fex
   * @return The series id of fex
   * @throws NotFoundException if fex not exists
   * @throws FexException      if exception occurs
   */
  String getFexSeriesId(String fexId) throws NotFoundException, FexException;

  /**
   * Gets if a fex is SBS or not
   *
   * @param fexId Id of fex
   * @return if fex is sbs or not
   * @throws NotFoundException if fex not exists
   * @throws FexException      if exception occurs
   */
  boolean isSbs(String fexId) throws NotFoundException, FexException;

  /**
   * Updates sbs status of a fex
   *
   * @param fexId ID of fex
   * @param sbs   new sbs-status of fex
   * @throws NotFoundException if fex not exists
   * @throws FexException      if exception occurs
   */
  void updateIsSbsStatus(String fexId, boolean sbs) throws NotFoundException, FexException;

  /**
   * Updates receiver of a fex
   *
   * @param fexId    ID of fex
   * @param receiver new receiver of fex
   * @throws NotFoundException if fex not exists
   * @throws FexException      if exception occurs
   */
  void updateReceiver(String fexId, String receiver) throws NotFoundException, FexException;

  /**
   * Updates lecture id of a fex
   *
   * @param fexId     ID of fex
   * @param lectureId new lecture id of fex
   * @throws NotFoundException if fex not exists
   * @throws FexException      if exception occurs
   */
  void updateLectureId(String fexId, String lectureId) throws NotFoundException, FexException;

  /**
   * Updates series id of a fex
   *
   * @param fexId    ID of fex
   * @param seriesId new series id of fex
   * @throws NotFoundException if fex not exists
   * @throws FexException      if exception occurs
   */
  void updateSeriesId(String fexId, String seriesId) throws NotFoundException, FexException;

  /**
   * Adds a fex in database
   *
   * @param fex
   * @throws FexException
   */
  void addFex(String fex) throws FexException;

  /**
   * get a fex by its series id
   *
   * @param seriesId
   * @return
   * @throws NotFoundException
   * @throws FexException
   */
  Fex getFexBySeriesId(String seriesId) throws NotFoundException, FexException;

}
