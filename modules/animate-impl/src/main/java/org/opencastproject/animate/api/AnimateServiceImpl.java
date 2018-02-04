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
import org.opencastproject.workspace.api.Workspace;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.osgi.service.cm.ManagedService;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.List;
import java.util.Map;

/** Inspects media via ffprobe. */
public class AnimateServiceImpl extends AbstractJobProducer implements AnimateService, ManagedService {

  public static final String SYNFIG_BINARY_CONFIG = "org.opencastproject.animate.synfig.path";

  private String synfigBinary = "synfig";

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

  private final Type stringMapType = new TypeToken<Map<String, String>>() { }.getType();

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
  }

  @Override
  public void updated(Dictionary properties) throws ConfigurationException {
    if (properties == null)
      return;

    /*
    inspectJobLoad = LoadUtil.getConfiguredLoadValue(properties, INSPECT_JOB_LOAD_KEY, DEFAULT_INSPECT_JOB_LOAD,
            serviceRegistry);
    */
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.job.api.AbstractJobProducer#process(org.opencastproject.job.api.Job)
   */
  @Override
  protected String process(Job job) throws Exception {
    if (!OPERATION.equals(job.getOperation())) {
      throw new ServiceRegistryException(String.format("This service can't handle operations of type '%s'", operation));
    }

    List<String> arguments = job.getArguments();
    File animation = new File(arguments.get(0));
    Gson gson = new Gson();
    Map<String, String> metadata = gson.fromJson(arguments.get(1), stringMapType);
    Map<String, String> options = gson.fromJson(arguments.get(2), stringMapType);

    // TODO: do work
    return "";
  }

  @Override
  public Job animate(File animation, Map<String, String> metadata, Map<String, String> options) throws
          AnimateServiceException {
    Gson gson = new Gson();
    List<String> arguments = Arrays.asList(animation.getAbsolutePath(), gson.toJson(metadata), gson.toJson(options));
    try {
      return serviceRegistry.createJob(JOB_TYPE, OPERATION, arguments, jobLoad);
    } catch (ServiceRegistryException e) {
      throw new AnimateServiceException(e);
    }
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
