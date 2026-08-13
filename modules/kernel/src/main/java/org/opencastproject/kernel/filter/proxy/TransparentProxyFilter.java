/*
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

package org.opencastproject.kernel.filter.proxy;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ServiceScope;
import org.osgi.service.component.propertytypes.ServiceRanking;
import org.osgi.service.http.whiteboard.propertytypes.HttpWhiteboardContextSelect;
import org.osgi.service.http.whiteboard.propertytypes.HttpWhiteboardFilterName;
import org.osgi.service.http.whiteboard.propertytypes.HttpWhiteboardFilterPattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

/**
 * This filter is looking for <code>X-Forwarded-For</code> headers in the HTTP request and, if the request came from a
 * trusted reverse proxy, sets the address found in it as the original IP.
 * <p>
 * It is the only place in Opencast which reads that header. Everything else asks the request for its remote address
 * and hence needs this filter to run first, which is what its high service ranking is for.
 */
@Component(
    service = Filter.class,
    scope = ServiceScope.PROTOTYPE,
    property = {
        "service.description=Transparent Proxy Filter",
    }
)
// Must run before every filter relying on the client address or on FORWARDED_HEADERS_TRUSTED, namely HttpsFilter (990),
// OrganizationFilter (980) and UrlSigningFilter (7)
@ServiceRanking(995)
@HttpWhiteboardFilterName("TransparentProxyFilter")
@HttpWhiteboardFilterPattern("/*")
@HttpWhiteboardContextSelect("(osgi.http.whiteboard.context.name=opencast)")
public class TransparentProxyFilter implements Filter {
  /** The logger */
  private static final Logger logger = LoggerFactory.getLogger(TransparentProxyFilter.class);

  /** Request header that is set when behind a proxy */
  public static final String X_FORWARDED_FOR = "X-Forwarded-For";

  /**
   * Request attribute set to <code>Boolean.TRUE</code> if the request was received from a trusted reverse proxy, so
   * that filters running later can tell whether the remaining <code>X-Forwarded-*</code> headers mean anything. They
   * cannot work this out themselves since the remote address has been rewritten by then.
   */
  public static final String FORWARDED_HEADERS_TRUSTED = "org.opencastproject.forwarded.trusted";

  /** The trusted proxy configuration */
  private TrustedProxies trustedProxies;

  /** OSGi DI */
  @Reference
  public void setTrustedProxies(TrustedProxies trustedProxies) {
    this.trustedProxies = trustedProxies;
  }

  /**
   * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest, javax.servlet.ServletResponse,
   *      javax.servlet.FilterChain)
   */
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException,
          ServletException {
    HttpServletRequest httpRequest = (HttpServletRequest) request;

    final String peer = httpRequest.getRemoteAddr();
    if (!trustedProxies.isTrusted(peer)) {
      logger.debug("Request from '{}' which is not a trusted proxy. Ignoring X-Forwarded-* headers.", peer);
      chain.doFilter(httpRequest, response);
      return;
    }

    httpRequest.setAttribute(FORWARDED_HEADERS_TRUSTED, Boolean.TRUE);

    final String client = trustedProxies.resolveClientAddress(peer, httpRequest.getHeader(X_FORWARDED_FOR));
    if (!peer.equals(client)) {
      logger.debug("Trusted proxy '{}' reported client IP '{}'", peer, client);
      httpRequest = new TransparentProxyRequestWrapper(httpRequest, client);
    }

    chain.doFilter(httpRequest, response);
  }

  public void init(FilterConfig filterConfig) throws ServletException {
  }

  public void destroy() {
  }

}
