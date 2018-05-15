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

package org.opencastproject.adminui.endpoint;

import static org.opencastproject.adminui.endpoint.EndpointUtil.generateJSONObject;

import org.opencastproject.adminui.exception.JsonCreationException;
import org.opencastproject.index.service.exception.ListProviderException;
import org.opencastproject.index.service.resources.list.api.ListProvidersService;
import org.opencastproject.index.service.resources.list.api.ResourceListQuery;
import org.opencastproject.index.service.resources.list.query.ResourceListQueryImpl;
import org.opencastproject.security.api.SecurityService;
import org.opencastproject.util.doc.rest.RestQuery;
import org.opencastproject.util.doc.rest.RestResponse;
import org.opencastproject.util.doc.rest.RestService;

import org.json.simple.JSONObject;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/")
@RestService(name = "StatsProviders", title = "Admin UI - Stats Filter List",
  abstractText = "This service provides a set of filters for the statistics view in the admin UI.",
  notes = { "This service provides a set of filters for the statistics view in the admin UI.",
            "<strong>Important:</strong> "
              + "<em>This service is for exclusive use by the module admin-ui. Its API might change "
              + "anytime without prior notice. Any dependencies other than the admin UI will be strictly ignored. "
              + "DO NOT use this for integration of third-party applications.<em>"})
public class StatsEndpoint {

  private static final Logger logger = LoggerFactory.getLogger(StatsEndpoint.class);

  public static final Response NOT_FOUND = Response.status(Response.Status.NOT_FOUND).build();
  public static final Response SERVER_ERROR = Response.serverError().build();

  private SecurityService securityService;
  private ListProvidersService listProvidersService;

  protected void activate(BundleContext bundleContext) {
    logger.info("Activate stats endpoint");
  }

  /** OSGi callback for listprovider service. */
  public void setListProvidersService(ListProvidersService listProvidersService) {
    this.listProvidersService = listProvidersService;
  }

  /** OSGi callback for security service. */
  public void setSecurityService(SecurityService securitySerivce) {
    this.securityService = securitySerivce;
  }

  @GET
  @Path("stats.json")
  @Produces(MediaType.APPLICATION_JSON)
  @RestQuery(name = "stats", description = "Provides a set of filters for the statistics view in the admin UI",
    reponses = { @RestResponse(description = "Returns a set of filters for the statistics view in the admin UI",
      responseCode = HttpServletResponse.SC_OK) }, returnDescription = "")
  public Response getStats(@Context HttpHeaders headers) {
    String provider = "STATS";
    ResourceListQuery query = new ResourceListQueryImpl();

    if (listProvidersService.hasProvider(provider)) {
      JSONObject stats;
      try {
        stats = generateJSONObject(listProvidersService.getList(provider, query, securityService.getOrganization(),
          false));
        return Response.ok(stats.toString()).build();
      } catch (JsonCreationException e) {
        logger.error("Not able to generate filters list JSON from source {}: {}", provider, e);
        return SERVER_ERROR;
      } catch (ListProviderException e) {
        logger.error("Not able to get list from provider {}: {}", provider, e);
        return SERVER_ERROR;
      }
    } else {
      return NOT_FOUND;
    }
  }
}
