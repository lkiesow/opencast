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

package org.opencastproject.workflow.handler.videoeditor;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.anyString;

import org.opencastproject.job.api.Job;
import org.opencastproject.job.api.JobImpl;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageBuilder;
import org.opencastproject.mediapackage.MediaPackageBuilderFactory;
import org.opencastproject.mediapackage.MediaPackageException;
import org.opencastproject.serviceregistry.api.ServiceRegistry;
import org.opencastproject.serviceregistry.api.ServiceRegistryException;
import org.opencastproject.smil.api.SmilException;
import org.opencastproject.smil.api.SmilService;
import org.opencastproject.smil.entity.api.Smil;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.videoeditor.api.ProcessFailedException;
import org.opencastproject.videoeditor.api.VideoEditorService;
import org.opencastproject.workflow.api.WorkflowInstance;
import org.opencastproject.workflow.api.WorkflowInstanceImpl;
import org.opencastproject.workflow.api.WorkflowOperationException;
import org.opencastproject.workflow.api.WorkflowOperationInstance;
import org.opencastproject.workflow.api.WorkflowOperationInstanceImpl;
import org.opencastproject.workflow.api.WorkflowOperationResult;
import org.opencastproject.workspace.api.Workspace;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBException;

/**
 * Test class for {@link VideoEditorWorkflowOperationHandler}
 */
public class VideoEditorWorkflowOperationHandlerTest {

  private VideoEditorWorkflowOperationHandler videoEditorWorkflowOperationHandler = null;
  private SmilService smilService = null;
  private VideoEditorService videoEditorServiceMock = null;
  private Workspace workspaceMock = null;

  private URI mpURI = null;
  private MediaPackage mp = null;
  private URI mpSmilURI = null;
  private MediaPackage mpSmil = null;

  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  @Before
  public void setUp() throws MediaPackageException, IOException, NotFoundException, URISyntaxException, SmilException,
          JAXBException, SAXException {

    MediaPackageBuilder mpBuilder = MediaPackageBuilderFactory.newInstance().newMediaPackageBuilder();
    mpURI = VideoEditorWorkflowOperationHandlerTest.class.getResource("/editor_mediapackage.xml").toURI();
    mp = mpBuilder.loadFromXml(mpURI.toURL().openStream());
    mpSmilURI = VideoEditorWorkflowOperationHandlerTest.class.getResource("/editor_smil_mediapackage.xml").toURI();
    mpSmil = mpBuilder.loadFromXml(mpSmilURI.toURL().openStream());
    videoEditorServiceMock = EasyMock.createMock(VideoEditorService.class);
    workspaceMock = EasyMock.createMock(Workspace.class);

    smilService = SmilServiceMock.createSmilServiceMock(mpSmilURI);

    videoEditorWorkflowOperationHandler = new VideoEditorWorkflowOperationHandler();
    videoEditorWorkflowOperationHandler.setJobBarrierPollingInterval(0);
    videoEditorWorkflowOperationHandler.setSmilService(smilService);
    videoEditorWorkflowOperationHandler.setVideoEditorService(videoEditorServiceMock);
    videoEditorWorkflowOperationHandler.setWorkspace(workspaceMock);
  }

  private static Map<String, String> getDefaultConfiguration() {
    Map<String, String> configuration = new HashMap<String, String>();
    configuration.put("source-flavors", "*/work");
    configuration.put("source-smil-flavor", "*/smil");
    configuration.put("target-smil-flavor", "episode/smil");
    configuration.put("target-flavor-subtype", "trimmed");
    return configuration;
  }

  private WorkflowInstanceImpl getWorkflowInstance(MediaPackage mp, Map<String, String> configurations) {
    WorkflowInstanceImpl workflowInstance = new WorkflowInstanceImpl();
    workflowInstance.setId(1);
    workflowInstance.setState(WorkflowInstance.WorkflowState.RUNNING);
    workflowInstance.setMediaPackage(mp);
    WorkflowOperationInstanceImpl operation = new WorkflowOperationInstanceImpl("op",
            WorkflowOperationInstance.OperationState.RUNNING);
    operation.setTemplate("editor");
    operation.setState(WorkflowOperationInstance.OperationState.RUNNING);
    for (String key : configurations.keySet()) {
      operation.setConfiguration(key, configurations.get(key));
    }
    List<WorkflowOperationInstance> operations = new ArrayList<WorkflowOperationInstance>(1);
    operations.add(operation);
    workflowInstance.setOperations(operations);
    return workflowInstance;
  }

  @Test
  public void testConfig() throws WorkflowOperationException, IOException {
    Map<String, String> configuration = new HashMap<>();
    String[] configKeys = new String[] {"source-smil-flavor", "target-smil-flavor", "source-flavors", "..."};
    for (String key: configKeys) {
      try {
        videoEditorWorkflowOperationHandler.start(getWorkflowInstance(mp, configuration), null);
        Assert.fail("No config is set. Should fail.");
      } catch (WorkflowOperationException e) {
        // we expect this to happen
      }
      configuration.put(key, "...");
    }
  }

  @Test
  public void testNoSourceSmil() throws WorkflowOperationException, IOException {
    WorkflowInstanceImpl workflowInstance = getWorkflowInstance(mp, getDefaultConfiguration());
    WorkflowOperationResult result = videoEditorWorkflowOperationHandler.start(workflowInstance, null);
    Assert.assertNotNull("editor operation should return an instantiated WorkflowOperationResult", result);
    WorkflowOperationInstance operation = workflowInstance.getCurrentOperation();
    Assert.assertEquals(result.getAction(), WorkflowOperationResult.Action.SKIP);
  }

  @Test
  public void testRun() throws WorkflowOperationException, IOException, NotFoundException, ProcessFailedException,
      ServiceRegistryException {
    File smilFile = testFolder.newFile("xy.smil");
    Files.copy(VideoEditorWorkflowOperationHandlerTest.class.getResourceAsStream("/editor_smil_filled.smil"),
        smilFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    EasyMock.expect(workspaceMock.get(anyObject(URI.class))).andReturn(smilFile).once();

    EasyMock.expect(workspaceMock.put(anyString(), anyString(), anyString(), anyObject(InputStream.class)))
        .andReturn(URI.create("http://localhost:8080/foo/presenter.smil"));

    JobImpl job = new JobImpl();
    job.setStatus(Job.Status.FINISHED);
    // payload needs to be track-xml
    EasyMock.expect(videoEditorServiceMock.processSmil(anyObject(Smil.class)))
        .andReturn(Collections.emptyList()).once();
        //.andReturn(Collections.singletonList(job)).once();

    ServiceRegistry serviceRegistry = EasyMock.createMock(ServiceRegistry.class);
    EasyMock.expect(serviceRegistry.getJob(EasyMock.anyLong())).andReturn(job).once();

    EasyMock.replay(workspaceMock, videoEditorServiceMock, serviceRegistry);


    WorkflowInstanceImpl workflowInstance = getWorkflowInstance(mpSmil, getDefaultConfiguration());
    videoEditorWorkflowOperationHandler.setServiceRegistry(serviceRegistry);
    WorkflowOperationResult result = videoEditorWorkflowOperationHandler.start(workflowInstance, null);
    Assert.assertNotNull("editor operation should return an instantiated WorkflowOperationResult", result);
    WorkflowOperationInstance operation = workflowInstance.getCurrentOperation();
    Assert.assertEquals(result.getAction(), WorkflowOperationResult.Action.CONTINUE);
  }

}
