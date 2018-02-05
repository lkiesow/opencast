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

package org.opencastproject.animate.impl;

import static org.easymock.EasyMock.capture;

import org.opencastproject.job.api.Job;
import org.opencastproject.job.api.JobImpl;
import org.opencastproject.serviceregistry.api.ServiceRegistry;
import org.opencastproject.util.IoSupport;
import org.opencastproject.workspace.api.Workspace;

import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnimateServiceImplTest {
  private static final Logger logger = LoggerFactory.getLogger(AnimateServiceImplTest.class);

  private static boolean runSynfigTests = false;

  private AnimateServiceImpl animateService;

  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  @BeforeClass
  public static void setupClass() {
    Process process = null;
    try {
      process = new ProcessBuilder(AnimateServiceImpl.SYNFIG_BINARY_DEFAULT).start();
      runSynfigTests = true;
    } catch (Throwable t) {
      logger.warn("Skipping tests due to unsatisfied synfig installation");
    } finally {
      IoSupport.closeQuietly(process);
    }
  }

  @Before
  public void setUp() throws Exception {
    // Skip tests if synfig is not installed
    Assume.assumeTrue(runSynfigTests);

    // create animate service
    animateService = new AnimateServiceImpl();

    // create the needed mocks
    BundleContext bc = EasyMock.createNiceMock(BundleContext.class);
    ComponentContext cc = EasyMock.createNiceMock(ComponentContext.class);
    EasyMock.expect(cc.getBundleContext()).andReturn(bc).anyTimes();

    Workspace workspace = EasyMock.createMock(Workspace.class);
    EasyMock.expect(workspace.rootDirectory()).andReturn(testFolder.newFolder().getAbsolutePath()).anyTimes();

    // Finish setting up the mocks
    EasyMock.replay(bc, cc, workspace);

    ServiceRegistry serviceRegistry = EasyMock.createMock(ServiceRegistry.class);
    final Capture<String> type = EasyMock.newCapture();
    final Capture<String> operation = EasyMock.newCapture();
    final Capture<List<String>> args = EasyMock.newCapture();
    EasyMock.expect(serviceRegistry.createJob(capture(type), capture(operation), capture(args), EasyMock.anyFloat()))
            .andAnswer(() -> {
              // you could do work here to return something different if you needed.
              Job job = new JobImpl(0);
              job.setJobType(type.getValue());
              job.setOperation(operation.getValue());
              job.setArguments(args.getValue());
              job.setPayload(animateService.process(job));
              return job;
            }).anyTimes();
    EasyMock.replay(serviceRegistry);

    animateService.setServiceRegistry(serviceRegistry);
    animateService.setWorkspace(workspace);
  }


  @Test
  public void testAnimate() throws Exception {
    File animation = new File(getClass().getResource("/synfig-test-animation.sif").toURI());
    Map<String, String> metadata = new HashMap<>();
    metadata.put("episode.title", "Test");
    metadata.put("episode.author", "John Doe");
    metadata.put("series.title", "The Art Of Animation");
    Map<String, String> options = new HashMap<>();
    options.put("-t", "ffmpeg");
    Job job = animateService.animate(animation, metadata, options);
    File output = new File(job.getPayload());
    Assert.assertTrue(output.isFile());
  }


  @Test
  public void testMissingEncodingOptions() throws Exception {
    File animation = new File(getClass().getResource("/synfig-test-animation.sif").toURI());
    Map<String, String> metadata = new HashMap<>();
    Map<String, String> options = new HashMap<>();
    Job job = null;
    try {
      job = animateService.animate(animation, metadata, options);
    } catch (Exception e) {
      // we expect this
    }
    Assert.assertNull(job);
  }
}
