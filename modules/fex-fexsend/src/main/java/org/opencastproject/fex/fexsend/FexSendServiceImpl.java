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
package org.opencastproject.fex.fexsend;

import org.opencastproject.fex.api.FexException;
import org.opencastproject.fex.api.FexSendService;
import org.opencastproject.fex.api.FexService;
import org.opencastproject.fex.objects.Fex;
import org.opencastproject.job.api.AbstractJobProducer;
import org.opencastproject.job.api.Job;
import org.opencastproject.mediapackage.MediaPackageElementParser;
import org.opencastproject.mediapackage.MediaPackageException;
import org.opencastproject.mediapackage.Track;
import org.opencastproject.security.api.OrganizationDirectoryService;
import org.opencastproject.security.api.SecurityService;
import org.opencastproject.security.api.UserDirectoryService;
import org.opencastproject.series.api.SeriesService;
import org.opencastproject.serviceregistry.api.ServiceRegistry;
import org.opencastproject.serviceregistry.api.ServiceRegistryException;
import org.opencastproject.util.LoadUtil;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.workspace.api.Workspace;

import org.apache.commons.lang3.StringUtils;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.List;

/**
 * Service that sends a fex via mail to receivers.
 */
public class FexSendServiceImpl extends AbstractJobProducer implements FexSendService, ManagedService {

  public static final String COLLECTION_ID = "seriesfiles";
  public static final String FEX_BINARY_CONFIG = "org.opencastproject.fex.path";
  public static final String FEX_BINARY_DEFAULT = "fex";
  public static final String FEX_ACCOUNTID_CONFIG = "org.opencastporject.fex.accountid";
  public static final String FEX_ACCOUNTID_DEFAULT = "aufzeichnungen-support";
  public static final float DEFAULT_FEX_JOB_LOAD = 1.0f;
  public static final String FEX_JOB_LOAD_KEY = "job.load.fex";
  protected static final Logger logger = LoggerFactory.getLogger(FexSendServiceImpl.class);
  protected String binary;
  protected String accountId;
  protected ServiceRegistry serviceRegistry = null;
  protected Workspace workspace = null;
  protected SecurityService securityService = null;
  protected UserDirectoryService userDirectoryService = null;
  protected OrganizationDirectoryService organizationDirectoryService = null;
  protected FexService fexService = null;
  protected SeriesService seriesService = null;
  private float fexJobload = DEFAULT_FEX_JOB_LOAD;

  public FexSendServiceImpl() {
    super(JOB_TYPE);
    this.binary = FEX_BINARY_DEFAULT;
    this.accountId = FEX_ACCOUNTID_DEFAULT;
  }

  @Override
  public void activate(ComponentContext cc) {
    super.activate(cc);
    final String path = cc.getBundleContext().getProperty(FEX_BINARY_CONFIG);
    final String account = cc.getBundleContext().getProperty(FEX_ACCOUNTID_CONFIG);
    this.binary = path == null ? FEX_BINARY_DEFAULT : path;
    this.accountId = account == null ? FEX_ACCOUNTID_DEFAULT : account;

    logger.info("Configuration {}: {}", FEX_BINARY_CONFIG, FEX_BINARY_DEFAULT);
  }

  @Override
  public void updated(Dictionary<String, ?> properties) throws ConfigurationException {
    if (properties == null) {
      return;
    }
    logger.debug("Configuring the fexSendService");

    fexJobload = LoadUtil.getConfiguredLoadValue(properties, FEX_JOB_LOAD_KEY, DEFAULT_FEX_JOB_LOAD, serviceRegistry);

  }

  @Override
  public Job fexSend(List<Track> seriesTracks, Fex fex) throws FexException, MediaPackageException {
    try {
      Job job = serviceRegistry.createJob(JOB_TYPE, Operation.Fex.toString(),
              Arrays.asList(MediaPackageElementParser.getArrayAsXml(seriesTracks), fex.getSeriesId()), fexJobload);
      try {
        process(job);
      } catch (Exception e) {
        logger.warn(e.getMessage());
      }
      return job;
    } catch (ServiceRegistryException e) {
      throw new FexException("Unable to create a job", e);
    }
  }

  protected List<File> fexSend(Job job, List<Track> seriesTracks, Fex fex) throws FexException, MediaPackageException {
    try {
      List<File> mediaTracks = new ArrayList();
      try {
        for (Track track : seriesTracks) {
          File mediaFile = workspace.get(track.getURI());
          URL mediaUrl = mediaFile.toURI().toURL();
          mediaTracks.add(mediaFile);
        }
      } catch (NotFoundException e) {
        throw new FexException("Error finding the Series in the Workspace", e);

      } catch (IOException e) {
        throw new FexException("Error reading the Series file in the workspace", e);
      }

      logger.info("Starting sending of {} via Fex", seriesTracks.get(0));

      sendFexPerl(mediaTracks, fex);
      return mediaTracks;

    } catch (Exception e) {
      logger.warn("Error sending fex series tracks", e);
      if (e instanceof FexException) {
        throw (FexException) e;
      } else {
        throw new FexException(e);
      }
    }

  }

  protected Fex sendFexPerl(List<File> seriesTracks, Fex fex) throws IOException, FexException {
    for (File seriesTrack : seriesTracks) {
      String[] command = new String[] { binary, "-i", accountId, seriesTrack.getPath(), fex.getReceiver() };
      String commandline = StringUtils.join(command, " ");

      logger.info("Running {}", commandline);

      ProcessBuilder pbuilder = new ProcessBuilder(command);
      pbuilder.redirectErrorStream(true);
      Process process = null;
      BufferedReader errSteam = null;
      int exitCode = 1;
      try {
        process = pbuilder.start();
        errSteam = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line = errSteam.readLine();
        while (line != null) {
          logger.debug(line);
          line = errSteam.readLine();
        }
        exitCode = process.waitFor();
      } catch (IOException ex) {
        throw new FexException("Start sendfex process failed", ex);
      } catch (InterruptedException ex) {
        throw new FexException("sendfex process failed", ex);
      }
      if (exitCode != 0) {
        throw new FexException("The encoder process exited abnormally with exit code " + exitCode);
      }
    }

    return fex;

  }

  @Override
  protected String process(Job job) throws Exception {
    Operation op = null;
    String operation = job.getOperation();
    List<String> arguments = job.getArguments();

    try {
      op = Operation.valueOf(operation);
      switch (op) {
        case Fex:

          List<Track> seriesTracks = Arrays.asList((Track) MediaPackageElementParser.getFromXml(arguments.get(0)));
          Fex fex = fexService.getFexBySeriesId(arguments.get(1));
          List<File> mediafiles = fexSend(job, seriesTracks, fex);
          return mediafiles.toString();
        default:
          throw new IllegalStateException("Don't know how to handle operation " + operation);
      }
    } catch (IllegalArgumentException e) {
      throw new ServiceRegistryException("This service can't handle operations of type " + op, e);

    } catch (IndexOutOfBoundsException e) {
      throw new ServiceRegistryException("This argument list for operation " + op + " does not meet expectations", e);
    } catch (Exception e) {
      throw new ServiceRegistryException("Error handling operation " + op, e);
    }
  }

  protected void setWorkspace(Workspace workspace) {
    this.workspace = workspace;
  }

  @Override
  protected ServiceRegistry getServiceRegistry() {
    return serviceRegistry;
  }

  protected void setServiceRegistry(ServiceRegistry serviceRegistry) {
    this.serviceRegistry = serviceRegistry;
  }

  @Override
  protected SecurityService getSecurityService() {
    return securityService;
  }

  protected void setSecurityService(SecurityService securityService) {
    this.securityService = securityService;
  }

  @Override
  protected UserDirectoryService getUserDirectoryService() {
    return userDirectoryService;
  }

  protected void setUserDirectoryService(UserDirectoryService userDirectoryService) {
    this.userDirectoryService = userDirectoryService;
  }

  @Override
  protected OrganizationDirectoryService getOrganizationDirectoryService() {
    return organizationDirectoryService;
  }

  protected void setOrganizationDirectoryService(OrganizationDirectoryService organizationDirectoryService) {
    this.organizationDirectoryService = organizationDirectoryService;
  }

  public void setFexService(FexService fexService) {
    this.fexService = fexService;
  }

  public void setSeriesService(SeriesService seriesService) {
    this.seriesService = seriesService;
  }

  private enum Operation {
    Fex
  }

}
