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

import org.opencastproject.job.api.Job;
import org.opencastproject.security.api.Organization;
import org.opencastproject.security.api.OrganizationDirectoryService;
import org.opencastproject.serviceregistry.api.ServiceRegistry;
import org.opencastproject.serviceregistry.api.SystemLoad;
import org.opencastproject.util.doc.rest.RestQuery;
import org.opencastproject.util.doc.rest.RestResponse;
import org.opencastproject.util.doc.rest.RestService;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.common.TextFormat;

/**
 * Opencast metrics endpoint
 */
@Component(
  property = {
    "service.description=Metrics Endpoint",
    "opencast.service.type=org.opencastproject.metrics",
    "opencast.service.path=/metrics",
    "opencast.service.jobproducer=false"
  },
  immediate = true,
  service = MetricsExporter.class
)
@Path("")
@RestService(name = "MetricsEndpoint",
    title = "Metrics Endpoint",
    abstractText = "Opencast metrics endpoint.",
    notes = { "The endpoints supports the <a href=https://openmetrics.io>OpenMetrics format</a>",
              "This can be used by <a href=https://prometheus.io>Prometheus</a>"})
public class MetricsExporter {
  /** The logger */
  private static final Logger logger = LoggerFactory.getLogger(MetricsExporter.class);

  // Prometheus metrics registry for exposing metrics
  private final CollectorRegistry registry = CollectorRegistry.defaultRegistry;

  private final Counter requests = Counter.build()
      .name("requests_total")
      .help("Total requests.")
      .register();
  private final Gauge jobLoadMax = Gauge.build()
      .name("opencast_job_load_max")
      .help("Maximum job load")
      .labelNames("host")
      .register();
  private final Gauge jobLoadCurrent = Gauge.build()
      .name("opencast_job_load_current")
      .help("Maximum job load")
      .labelNames("host")
      .register();
  private final Gauge jobsActive = Gauge.build()
      .name("opencast_job_active")
      .help("Active jobs")
      .labelNames("host", "organization")
      .register();
  private final Gauge workflowsActive = Gauge.build()
      .name("opencast_workflow_active")
      .help("Active workflows")
      .labelNames("organization")
      .register();

  /** The service */
  private ServiceRegistry serviceRegistry;
  private OrganizationDirectoryService organizationDirectoryService;


  @GET
  @Path("/")
  @Produces(TextFormat.CONTENT_TYPE_004)
  //@Produces(MediaType.TEXT_PLAIN)
  @RestQuery(name = "metrics",
      description = "Metrics about Opencast",
      responses = {@RestResponse(description = "Metrics", responseCode = HttpServletResponse.SC_OK)},
      returnDescription = "OpenMetrics about Opencast.")
  public Response metrics() throws Exception {
    Map<String, Integer> workflows = new HashMap<>();
    Map<String, Map<String, Integer>> jobs = new HashMap<>();
    for (Organization organization: organizationDirectoryService.getOrganizations()) {
      workflows.put(organization.getId(), 0);
      jobs.put(organization.getId(), new HashMap<>());
    }

    requests.inc();
    for (SystemLoad.NodeLoad nodeLoad: serviceRegistry.getCurrentHostLoads().getNodeLoads()) {
      jobLoadCurrent.labels(nodeLoad.getHost()).set(nodeLoad.getCurrentLoad());
      jobLoadMax.labels(nodeLoad.getHost()).set(nodeLoad.getMaxLoad());
      for (Map.Entry<String, Map<String, Integer>> entry: jobs.entrySet()) {
        entry.getValue().put(nodeLoad.getHost(), 0);
      }
    }
    for (Job job: serviceRegistry.getActiveJobs()) {
      Map<String, Integer> orgJobs = jobs.getOrDefault(job.getOrganization(), null);
      if (orgJobs != null) {
        orgJobs.computeIfPresent(job.getProcessingHost(), (k, v) -> v + 1);
      }
      if ("START_WORKFLOW".equals(job.getOperation())) {
        workflows.computeIfPresent(job.getOrganization(), (k, v) -> v + 1);
      }
    }

    for (Map.Entry<String, Integer> entry: workflows.entrySet()) {
      workflowsActive.labels(entry.getKey()).set(entry.getValue());
    }

    for (Map.Entry<String, Map<String, Integer>> entry: jobs.entrySet()) {
      for (Map.Entry<String, Integer> orgEntry: entry.getValue().entrySet()) {
        jobsActive.labels(orgEntry.getKey(), entry.getKey()).set(orgEntry.getValue());
      }
    }
    final StringWriter writer = new StringWriter();
    TextFormat.write004(writer, registry.metricFamilySamples());
    return Response.ok().entity(writer.toString()).build();
  }

  @Reference
  public void setServiceRegistry(ServiceRegistry service) {
    this.serviceRegistry = service;
  }

  @Reference
  public void setOrganizationDirectoryService(OrganizationDirectoryService organizationDirectoryService) {
    this.organizationDirectoryService = organizationDirectoryService;
  }
}
