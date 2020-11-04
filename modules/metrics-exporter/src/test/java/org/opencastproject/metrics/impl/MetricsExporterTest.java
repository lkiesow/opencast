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

package org.opencastproject.metrics.impl;

import org.opencastproject.security.api.DefaultOrganization;
import org.opencastproject.security.api.Organization;
import org.opencastproject.security.api.OrganizationDirectoryService;
import org.opencastproject.serviceregistry.api.ServiceRegistry;
import org.opencastproject.serviceregistry.api.SystemLoad;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

/**
 * Test the metrics endpoint
 */
public class MetricsExporterTest {

  private static final Logger logger = LoggerFactory.getLogger(MetricsExporterTest.class);

  private MetricsExporter exporter;

  /**
   * Setup for the Hello World Service
   */
  @Before
  public void setUp() {
    exporter = new MetricsExporter();
  }

  @Test
  public void testMetrics() throws Exception {
    SystemLoad systemLoad = new SystemLoad();
    SystemLoad.NodeLoad nodeLoad = new SystemLoad.NodeLoad();
    nodeLoad.setHost("opencast.org");
    nodeLoad.setMaxLoad(12.3F);
    nodeLoad.setCurrentLoad(1.23F);
    systemLoad.addNodeLoad(nodeLoad);
    ServiceRegistry serviceRegistry = EasyMock.createMock(ServiceRegistry.class);
    EasyMock.expect(serviceRegistry.getCurrentHostLoads()).andReturn(systemLoad).anyTimes();
    EasyMock.expect(serviceRegistry.getActiveJobs()).andReturn(Collections.emptyList()).anyTimes();

    Organization organization = new DefaultOrganization();
    OrganizationDirectoryService directoryService = EasyMock.createMock(OrganizationDirectoryService.class);
    EasyMock.expect(directoryService.getOrganizations()).andReturn(Collections.singletonList(organization)).anyTimes();

    EasyMock.replay(serviceRegistry, directoryService);
    exporter.setServiceRegistry(serviceRegistry);
    exporter.setOrganizationDirectoryService(directoryService);

    final String body = exporter.metrics().getEntity().toString();
    Assert.assertTrue(body.contains("opencast_job_load_max{host=\"opencast.org\",} 12.3"));
  }
}
