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


package org.opencastproject.search.api;

import org.opencastproject.job.api.Job;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageException;
import org.opencastproject.security.api.Organization;
import org.opencastproject.security.api.UnauthorizedException;
import org.opencastproject.serviceregistry.api.ServiceRegistryException;
import org.opencastproject.util.NotFoundException;

import org.apache.commons.lang3.tuple.Pair;

import java.util.Collection;

/**
 * Provides search capabilities, possibly to the engage tools, possibly to other services.
 */
public interface SearchService {

  /**
   * Identifier for service registration and location
   */
  String JOB_TYPE = "org.opencastproject.search";

  /**
   * Adds the media package to the search index.
   *
   * @param mediaPackage
   *          the media package
   * @throws SearchException
   *           if an error occurs while adding the media package
   * @throws MediaPackageException
   *           if an error occurs accessing the media package
   * @throws UnauthorizedException
   *           if the current user is not authorized to add this media package to the search index
   * @throws ServiceRegistryException
   *           if the job for adding the mediapackage can not be created
   */
  Job add(MediaPackage mediaPackage) throws SearchException, MediaPackageException, UnauthorizedException,
          ServiceRegistryException;

  Collection<Pair<Organization, MediaPackage>> getSeries(String seriesId);

  /**
   * Removes the media package identified by <code>mediaPackageId</code> from the search index.
   *
   * @param mediaPackageId
   *          id of the media package to remove
   * @return <code>true</code> if the episode was found and deleted
   * @throws SearchException
   *           if an error occurs while removing the media package
   * @throws UnauthorizedException
   *           if the current user is not authorized to remove this mediapackage from the search index
   */
  Job delete(String mediaPackageId) throws SearchException, UnauthorizedException, NotFoundException;

  MediaPackage get(String mediaPackageId) throws NotFoundException, SearchException, UnauthorizedException;
}
