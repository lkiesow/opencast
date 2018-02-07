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

import org.opencastproject.animate.api.AnimateService;
import org.opencastproject.animate.api.AnimateServiceException;
import org.opencastproject.job.api.AbstractJobProducer;
import org.opencastproject.job.api.Job;
import org.opencastproject.security.api.OrganizationDirectoryService;
import org.opencastproject.security.api.SecurityService;
import org.opencastproject.security.api.UserDirectoryService;
import org.opencastproject.serviceregistry.api.ServiceRegistry;
import org.opencastproject.serviceregistry.api.ServiceRegistryException;
import org.opencastproject.util.ConfigurationException;
import org.opencastproject.util.IoSupport;
import org.opencastproject.workspace.api.Workspace;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Create video animations using Synfig */
public class AnimateServiceImpl extends AbstractJobProducer implements AnimateService, ManagedService {

  public static final String SYNFIG_BINARY_CONFIG = "org.opencastproject.animate.synfig.path";

  public static final String SYNFIG_BINARY_DEFAULT = "synfig";

  private String synfigBinary = SYNFIG_BINARY_DEFAULT;

  /** The load introduced on the system by creating an inspect job */
  public static final float DEFAULT_JOB_LOAD = 1.0f;

  /** The load introduced on the system by creating an inspect job */
  private float jobLoad = DEFAULT_JOB_LOAD;

  private static final Logger logger = LoggerFactory.getLogger(AnimateServiceImpl.class);

  /** List of available operations on jobs */
  private static final String OPERATION = "animate";

  private Workspace workspace;
  private ServiceRegistry serviceRegistry;
  private SecurityService securityService;
  private UserDirectoryService userDirectoryService;
  private OrganizationDirectoryService organizationDirectoryService;

  private final Set<Process> activeProcesses = new HashSet<>();

  private static final Type stringMapType = new TypeToken<Map<String, String>>() { }.getType();
  private static final Type stringListType = new TypeToken<List<String>>() { }.getType();

  /** Creates a new animate service instance. */
  public AnimateServiceImpl() {
    super(JOB_TYPE);
  }

  @Override
  public void activate(ComponentContext cc) {
    super.activate(cc);
    // Get synfig path
    final String path = cc.getBundleContext().getProperty(SYNFIG_BINARY_CONFIG);
    if (path != null) {
      synfigBinary = path;
    }
    logger.debug("Activated animate service");
  }

  @Override
  public void updated(Dictionary properties) throws ConfigurationException {
    if (properties == null)
      return;

    /*
    inspectJobLoad = LoadUtil.getConfiguredLoadValue(properties, INSPECT_JOB_LOAD_KEY, DEFAULT_INSPECT_JOB_LOAD,
            serviceRegistry);
    */
    logger.debug("Updated animate service");
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.job.api.AbstractJobProducer#process(org.opencastproject.job.api.Job)
   */
  @Override
  protected String process(Job job) throws Exception {
    logger.debug("Started processing job {}", job.getId());
    if (!OPERATION.equals(job.getOperation())) {
      throw new ServiceRegistryException(String.format("This service can't handle operations of type '%s'",
              job.getOperation()));
    }

    List<String> arguments = job.getArguments();
    String animation = arguments.get(0);
    Gson gson = new Gson();
    Map<String, String> metadata = gson.fromJson(arguments.get(1), stringMapType);
    List<String> options = gson.fromJson(arguments.get(2), stringListType);

    // filter animation and get new, custom input file
    File input = customAnimation(job, animation, metadata);

    // prepare output file
    File output = new File(workspace.rootDirectory(), String.format("animate/%d/%s.%s", job.getId(),
            FilenameUtils.getBaseName(animation), "mp4"));
    FileUtils.forceMkdirParent(output);

    // create animation process.
    final List<String> command = new ArrayList<>();
    command.add(synfigBinary);
    command.add("-i");
    command.add(input.getAbsolutePath());
    command.add("-o");
    command.add(output.getAbsolutePath());
    command.addAll(options);
    logger.info("Executing animation command: {}", command);

    Process process = null;
    try {
      ProcessBuilder processBuilder = new ProcessBuilder(command);
      processBuilder.redirectErrorStream(true);
      process = processBuilder.start();
      activeProcesses.add(process);

      // print synfig (+ffmpeg) output
      try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        String line;
        while ((line = in.readLine()) != null) {
          logger.debug("synfig: {}", line);
        }
      }

      // wait until the task is finished
      int exitCode = process.waitFor();
      if (exitCode != 0) {
        throw new AnimateServiceException(String.format("Synfig exited abnormally with status %d (command: %s)",
                exitCode, command));
      }
      if (!output.isFile()) {
        throw new AnimateServiceException("Synfig produced no output");
      }
      logger.info("Animation generated successfully: {}", output);
    } catch (Exception e) {
      // Ensure temporary data are removed
      FileUtils.deleteQuietly(output.getParentFile());
      logger.debug("Removed output directory of failed animation process: {}", output.getParentFile());
      throw new AnimateServiceException(e);
    } finally {
      IoSupport.closeQuietly(process);
      FileUtils.deleteQuietly(input);
      activeProcesses.remove(process);
    }

    return output.getAbsolutePath();
  }


  private File customAnimation(final Job job, final String input, final Map<String, String> metadata)
          throws IOException {
    logger.debug("Start customizing the animation");
    File output = new File(workspace.rootDirectory(), String.format("animate/%d/%s.%s", job.getId(),
            FilenameUtils.getBaseName(input), FilenameUtils.getExtension(input)));
    FileUtils.forceMkdirParent(output);
    String animation = FileUtils.readFileToString(new File(input), "utf-8");

    // replace all metadata
    for (Map.Entry<String, String> entry: metadata.entrySet()) {
      String value = StringEscapeUtils.escapeXml11(entry.getValue());
      animation = animation.replaceAll("\\{\\{" + entry.getKey() + "\\}\\}", value);
    }

    // write new animation file
    FileUtils.write(output, animation, "utf-8");

    return output;
  }


  @Override
  public Job animate(File animation, Map<String, String> metadata, List<String> options) throws AnimateServiceException {
    Gson gson = new Gson();
    List<String> arguments = Arrays.asList(animation.getAbsolutePath(), gson.toJson(metadata), gson.toJson(options));
    try {
      logger.debug("Create animate service job");
      return serviceRegistry.createJob(JOB_TYPE, OPERATION, arguments, jobLoad);
    } catch (ServiceRegistryException e) {
      throw new AnimateServiceException(e);
    }
  }

  @Override
  public void cleanup(Job job) {
    logger.debug("Clean up animation job workspace");
    FileUtils.deleteQuietly(new File(workspace.rootDirectory(), String.format("animate/%d", job.getId())));
  }

  @Override
  protected ServiceRegistry getServiceRegistry() {
    return serviceRegistry;
  }

  @Override
  protected SecurityService getSecurityService() {
    return securityService;
  }

  @Override
  protected UserDirectoryService getUserDirectoryService() {
    return userDirectoryService;
  }

  @Override
  protected OrganizationDirectoryService getOrganizationDirectoryService() {
    return organizationDirectoryService;
  }

  public void setWorkspace(Workspace workspace) {
    this.workspace = workspace;
  }

  public void setServiceRegistry(ServiceRegistry jobManager) {
    this.serviceRegistry = jobManager;
  }

  public void setSecurityService(SecurityService securityService) {
    this.securityService = securityService;
  }

  public void setUserDirectoryService(UserDirectoryService userDirectoryService) {
    this.userDirectoryService = userDirectoryService;
  }

  public void setOrganizationDirectoryService(OrganizationDirectoryService organizationDirectoryService) {
    this.organizationDirectoryService = organizationDirectoryService;
  }
}
