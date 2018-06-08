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

import org.opencastproject.fex.impl.persistence.FexEntity;
import org.opencastproject.fex.objects.Fex;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.util.data.Tuple;

import java.util.List;

/**
 * Created by ac129583 on 8/18/17.
 * API that defines persistent storage of fex.
 */
public interface FexServiceDatabase {
  /**
   * Store (or update) fex.
   *
   * @return Dublin Core catalog representing newly created Fex or null if fex Dublin Core was updated
   * @throws FexServiceDatabaseException if exception occurs
   */
  FexEntity storeFex(String fexString) throws FexServiceDatabaseException;

  /**
   * Removes fex from persistent storage.
   *
   * @param fexId ID of the fex to be removed
   * @throws FexServiceDatabaseException if exception occurs
   * @throws NotFoundException           if fex with specified ID is not found
   */
  void deleteFex(String fexId) throws FexServiceDatabaseException, NotFoundException;

  /**
   * Returns all fex in persistent storage.
   *
   * @return {@link Tuple} array representing stored series
   * @throws FexServiceDatabaseException if exception occurs
   */
  List<Fex> getAllFex() throws FexServiceDatabaseException;

  /**
   * Gets a single fex by its identifier.
   *
   * @param fexId the fex identifier
   * @return fex
   * @throws NotFoundException           if there is no fex with this identifier
   * @throws FexServiceDatabaseException if there is a problem communicating with the underlying data store
   */
  FexEntity getFex(String fexId) throws NotFoundException, FexServiceDatabaseException;

  /**
   * Gets a single fex by its series identifier
   *
   * @param seriesId
   * @return
   * @throws NotFoundException
   * @throws FexServiceDatabaseException
   */
  FexEntity getFexBySeriesId(String seriesId) throws NotFoundException, FexServiceDatabaseException;

  /**
   * counts number of fex
   *
   * @return number of fex as Integer
   * @throws FexServiceDatabaseException
   */
  int countFex() throws FexServiceDatabaseException;

  /**
   * Gets receiver of a fex
   *
   * @param fexId the fex identifier
   * @return the receiver of this fex
   * @throws NotFoundException           if there is no fex with this id
   * @throws FexServiceDatabaseException id exception occurs
   */
  String getFexReceiver(String fexId) throws NotFoundException, FexServiceDatabaseException;

  /**
   * Gets lecture id of a fex
   *
   * @param fexId the fex identifier
   * @return the lecture id of this fex
   * @throws NotFoundException           if there is no fex with this id
   * @throws FexServiceDatabaseException if exception occurs
   */
  String getFexLectureId(String fexId) throws NotFoundException, FexServiceDatabaseException;

  /**
   * Gets series id of a fex
   *
   * @param fexId
   * @return series id of this fex
   * @throws NotFoundException
   * @throws FexServiceDatabaseException
   */
  String getFexSeriesId(String fexId) throws NotFoundException, FexServiceDatabaseException;

  /**
   * @param fexId
   * @return
   * @throws NotFoundException
   * @throws FexServiceDatabaseException
   */
  boolean isSbs(String fexId) throws NotFoundException, FexServiceDatabaseException;

  /**
   * Updates receiver of a fex
   *
   * @param fexId
   * @param receiver
   * @throws NotFoundException
   * @throws FexServiceDatabaseException
   */
  void updateReceiver(String fexId, String receiver) throws NotFoundException, FexServiceDatabaseException;

  /**
   * Updates lecture id of a fex
   *
   * @param fexId
   * @param lectureId
   * @throws NotFoundException
   * @throws FexServiceDatabaseException
   */
  void updateLectureId(String fexId, String lectureId) throws NotFoundException, FexServiceDatabaseException;

  /**
   * Updates series id of a fex
   *
   * @param fexId
   * @param seriesId
   * @throws NotFoundException
   * @throws FexServiceDatabaseException
   */
  void updateSeriesId(String fexId, String seriesId) throws NotFoundException, FexServiceDatabaseException;

  /**
   * Updates sbs status of a fex
   *
   * @param fexId
   * @param sbs
   * @throws NotFoundException
   * @throws FexServiceDatabaseException
   */
  void updateSbs(String fexId, boolean sbs) throws NotFoundException, FexServiceDatabaseException;

}
