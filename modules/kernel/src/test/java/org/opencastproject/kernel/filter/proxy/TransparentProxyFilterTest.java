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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Tests the {@link TransparentProxyFilter}
 */
public class TransparentProxyFilterTest {

  private static final String PROXY = "10.0.0.1";

  private static final String CLIENT = "93.184.216.34";

  private TransparentProxyFilter filter;

  /** The request the filter chain was called with */
  private HttpServletRequest forwarded;

  /** Attributes set on the request under test */
  private Map<String, Object> attributes;

  private FilterChain chain;

  @Before
  public void setUp() {
    TrustedProxies trustedProxies = new TrustedProxies();
    trustedProxies.modified(Map.of(TrustedProxies.TRUSTED_PROXIES_KEY, "10.0.0.0/8"));

    filter = new TransparentProxyFilter();
    filter.setTrustedProxies(trustedProxies);

    attributes = new HashMap<>();
    forwarded = null;

    chain = new FilterChain() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
        forwarded = (HttpServletRequest) request;
      }
    };
  }

  private void run(String peer, String forwardedFor) throws Exception {
    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getRemoteAddr()).andStubReturn(peer);
    EasyMock.expect(request.getRemoteHost()).andStubReturn(peer);
    EasyMock.expect(request.getHeader(TransparentProxyFilter.X_FORWARDED_FOR)).andStubReturn(forwardedFor);
    request.setAttribute(EasyMock.anyString(), EasyMock.anyObject());
    EasyMock.expectLastCall().andStubAnswer(() -> {
      attributes.put((String) EasyMock.getCurrentArguments()[0], EasyMock.getCurrentArguments()[1]);
      return null;
    });
    EasyMock.expect(request.getAttribute(EasyMock.anyString()))
        .andStubAnswer(() -> attributes.get((String) EasyMock.getCurrentArguments()[0]));
    EasyMock.replay(request);

    filter.doFilter(request, EasyMock.createNiceMock(HttpServletResponse.class), chain);
  }

  @Test
  public void testTrustedProxyReplacesTheAddress() throws Exception {
    run(PROXY, CLIENT);
    assertEquals(CLIENT, forwarded.getRemoteAddr());
    assertEquals(CLIENT, forwarded.getRemoteHost());
    assertTrue((Boolean) attributes.get(TransparentProxyFilter.FORWARDED_HEADERS_TRUSTED));
  }

  @Test
  public void testUntrustedPeerIsLeftAlone() throws Exception {
    run(CLIENT, "192.0.2.7");
    assertEquals("the spoofed header must be ignored", CLIENT, forwarded.getRemoteAddr());
    assertNull(attributes.get(TransparentProxyFilter.FORWARDED_HEADERS_TRUSTED));
  }

  @Test
  public void testTrustedProxyWithoutHeader() throws Exception {
    run(PROXY, null);
    assertEquals(PROXY, forwarded.getRemoteAddr());
    assertTrue("later filters still need to know the peer is a proxy",
        (Boolean) attributes.get(TransparentProxyFilter.FORWARDED_HEADERS_TRUSTED));
  }

  @Test
  public void testChainYieldsASingleAddress() throws Exception {
    run(PROXY, "192.0.2.7, " + CLIENT);
    assertEquals(CLIENT, forwarded.getRemoteAddr());
  }

}
