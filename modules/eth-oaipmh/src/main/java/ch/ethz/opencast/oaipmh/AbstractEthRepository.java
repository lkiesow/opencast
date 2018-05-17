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
package ch.ethz.opencast.oaipmh;

import static org.opencastproject.oaipmh.util.OsgiUtil.getContextProperty;

import org.opencastproject.oaipmh.Granularity;
import org.opencastproject.oaipmh.matterhorn.MatterhornInlinedMetadataProvider;
import org.opencastproject.oaipmh.matterhorn.MatterhornMetadataProvider;
import org.opencastproject.oaipmh.persistence.OaiPmhDatabase;
import org.opencastproject.oaipmh.server.MetadataProvider;
import org.opencastproject.oaipmh.server.OaiPmhRepository;
import org.opencastproject.oaipmh.server.ResumableQuery;
import org.opencastproject.oaipmh.util.ResumptionTokenStore;
import org.opencastproject.util.data.Collections;
import org.opencastproject.util.data.Option;

import org.osgi.service.component.ComponentContext;

import java.util.List;


/**
 * Base implementation of the ETHz OAI-PMH repository.
 */
public abstract class AbstractEthRepository extends OaiPmhRepository {
  private static final String PROP_ADMIN_EMAIL = "org.opencastproject.admin.email";

  private final ResumptionTokenStore tokenStore = ResumptionTokenStore.create();
  private List<MetadataProvider> metadataProviders = Collections.<MetadataProvider>list(
            new MatterhornMetadataProvider(),
            new MatterhornInlinedMetadataProvider());

  private OaiPmhDatabase persistence;
  private String adminEmail;

  /** OSGi DI */
  public void setPersistence(OaiPmhDatabase persistence) {
    this.persistence = persistence;
  }

  /** OSGi callback */
  public void activate(ComponentContext cc) {
    adminEmail = getContextProperty(cc, PROP_ADMIN_EMAIL);
  }

  @Override
  public Granularity getRepositoryTimeGranularity() {
    return Granularity.DAY;
  }

  @Override
  public OaiPmhDatabase getPersistence() {
    return persistence;
  }

  @Override
  public String getAdminEmail() {
    return adminEmail;
  }

  @Override
  public String saveQuery(ResumableQuery query) {
    return tokenStore.put(query);
  }

  @Override
  public Option<ResumableQuery> getSavedQuery(String resumptionToken) {
    return tokenStore.get(resumptionToken);
  }

  @Override
  public int getResultLimit() {
    return 50;
  }

  @Override
  public List<MetadataProvider> getRepositoryMetadataProviders() {
    return metadataProviders;
  }
}
