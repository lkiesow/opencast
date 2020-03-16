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
package org.opencastproject.distribution.ilias;

import static java.lang.String.format;
import static org.opencastproject.util.PathSupport.path;
import static org.opencastproject.util.RequireUtil.notNull;

import org.opencastproject.distribution.api.AbstractDistributionService;
import org.opencastproject.distribution.api.DistributionException;
import org.opencastproject.distribution.api.DistributionService;
import org.opencastproject.job.api.Job;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageElement;
import org.opencastproject.mediapackage.MediaPackageElementParser;
import org.opencastproject.mediapackage.MediaPackageException;
import org.opencastproject.mediapackage.MediaPackageParser;
import org.opencastproject.security.api.OrganizationDirectoryService;
import org.opencastproject.security.api.SecurityService;
import org.opencastproject.security.api.UserDirectoryService;
import org.opencastproject.serviceregistry.api.ServiceRegistry;
import org.opencastproject.serviceregistry.api.ServiceRegistryException;
import org.opencastproject.util.FileSupport;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.util.OsgiUtil;
import org.opencastproject.util.UrlSupport;
import org.opencastproject.workspace.api.Workspace;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

/**
 * Distributes media to the ilias media delivery directory.
 */
public class IliasDistributionServiceImpl extends AbstractDistributionService implements DistributionService {

  /** Logging facility */
  private static final Logger logger = LoggerFactory.getLogger(IliasDistributionServiceImpl.class);

  /** List of available operations on jobs */
  private enum Operation {
    Distribute, Retract
  }

  /** Receipt type */
  public static final String JOB_TYPE = "org.opencastproject.distribution.ilias";

  /** Default distribution directory */
  public static final String DEFAULT_DISTRIBUTION_DIR = "opencast" + File.separator + "ilias";

  /** Default directory for mediapackages without a series */
  public static final String DEFAULT_NOSERIES_DIR = "noSeriesDefined";

  /** Timeout in millis for checking distributed file request */
  private static final long TIMEOUT = 10000L;

  /** Interval time in millis for checking distributed file request */
  private static final long INTERVAL = 300L;

  /** Path to the distribution directory */
  protected File distributionDirectory = null;

  /** the base url for the ilias */
  protected String iliasUrl = null;

  /** part of the url for the plugin inside the ilias installation */
  protected String pluginBaseURL = null;

  /** The remote service registry */
  protected ServiceRegistry serviceRegistry = null;

  /** The workspace reference */
  protected Workspace workspace = null;

  /** The security service */
  protected SecurityService securityService = null;

  /** The user directory service */
  protected UserDirectoryService userDirectoryService = null;

  /** The organization directory service */
  protected OrganizationDirectoryService organizationDirectoryService = null;

  /** The Gson Json-Converter to user */
  private Gson gson = new Gson();

  /**
   * Creates a new instance of the download distribution service.
   */
  public IliasDistributionServiceImpl() {
    super(JOB_TYPE);
  }

  /**
   * Activate method for this OSGi service implementation.
   *
   * @param cc
   *          the OSGi component context
   */
  @Override
  public void activate(ComponentContext cc) {
    iliasUrl = cc.getBundleContext().getProperty("org.opencastproject.ilias.url");
    if (iliasUrl == null)
      throw new IllegalStateException("ILIAS download url must be set (org.opencastproject.ilias.url)");

    pluginBaseURL = cc.getBundleContext().getProperty("org.opencastproject.ilias.pluginpath");
    if (pluginBaseURL == null)
      throw new IllegalStateException("ILIAS buildpath prefix must be set (org.opencastproject.ilias.pluginpath)");

    String ccDistributionDirectory = cc.getBundleContext().getProperty("org.opencastproject.ilias.directory");
    if (ccDistributionDirectory == null)
      throw new IllegalStateException("ILIAS distribution directory must be set (org.opencastproject.ilias.directory)");
    this.distributionDirectory = new File(ccDistributionDirectory);
    logger.info("ILIAS download distribution directory is {}", distributionDirectory);
    this.distributionChannel = OsgiUtil.getComponentContextProperty(cc, CONFIG_KEY_STORE_TYPE);
  }

  public String getDistributionType() {
    return this.distributionChannel;
  }

  @Override
  public Job distribute(String channelId, MediaPackage mediapackage, String elementId)
          throws DistributionException, MediaPackageException {
    return distribute(channelId, mediapackage, elementId, true);
  }

  // @Override
  public Job distribute(String channelId, MediaPackage mediapackage, String elementId, boolean checkAvailability)
          throws DistributionException, MediaPackageException {
    notNull(mediapackage, "mediapackage");
    notNull(elementId, "elementId");
    notNull(channelId, "channelId");
    try {
      return serviceRegistry.createJob(JOB_TYPE, Operation.Distribute.toString(), Arrays.asList(channelId,
              MediaPackageParser.getAsXml(mediapackage), gson.toJson(elementId), Boolean.toString(checkAvailability)));
    } catch (ServiceRegistryException e) {
      throw new DistributionException("Unable to create a job", e);
    }
  }

  /**
   * Distribute a Mediapackage element to the download distribution service.
   * 
   * @param channelId
   *          The channelID to which the package shall be distributed
   * @param mediapackage
   *          The media package that contains the element to distribute.
   * @param elementId
   *          The id of the element that should be distributed contained within the media package.
   * @param checkAvailability
   *          Check the availability of the distributed element via http.
   * @return A reference to the MediaPackageElement that has been distributed.
   * @throws DistributionException
   *           Thrown if the parent directory of the MediaPackageElement cannot be created, if the MediaPackageElement
   *           cannot be copied or another unexpected exception occurs.
   */
  public MediaPackageElement distributeElement(String channelId, MediaPackage mediapackage, String elementId,
          boolean checkAvailability) throws DistributionException {
    notNull(mediapackage, "mediapackage");
    notNull(elementId, "elementId");
    notNull(channelId, "channelId");

    final String mediapackageId = mediapackage.getIdentifier().compact();
    final MediaPackageElement element = mediapackage.getElementById(elementId);
    if ("manifest.xml".equals(elementId)) {
      logger.info("Going to publish manifest, not checking if available in filesystem");
      final String directoryName = distributionDirectory.getAbsolutePath();
      final String seriesName = (null != mediapackage.getSeries()) ? mediapackage.getSeries() : DEFAULT_NOSERIES_DIR;
      String distdir = path(directoryName, seriesName, mediapackage.getIdentifier().compact(), "manifest.xml");
      try {
        FileUtils.writeStringToFile(new File(distdir), MediaPackageParser.getAsXml(mediapackage), "UTF-8");
      } catch (IOException e) {
        throw new DistributionException(e);
      }
      logger.info(format("Finished distributing element %s@%s for publication channel %s", elementId, mediapackageId,
              channelId));
      return null;
    } else {
      // Make sure the element exists
      if (mediapackage.getElementById(elementId) == null)
        throw new IllegalStateException(format("No element %s found in mediapackage %s", elementId, mediapackageId));

      try {
        File source;
        try {
          source = workspace.get(element.getURI());
        } catch (NotFoundException e) {
          throw new DistributionException("Unable to find " + element.getURI() + " in the workspace", e);
        } catch (IOException e) {
          throw new DistributionException("Error loading " + element.getURI() + " from the workspace", e);
        }
        File destination = getDistributionFile(channelId, mediapackage, element);

        // Put the file in place
        try {
          FileUtils.forceMkdir(destination.getParentFile());
        } catch (IOException e) {
          throw new DistributionException("Unable to create " + destination.getParentFile(), e);
        }
        logger.info(format("Distributing %s@%s for publication channel %s to %s", elementId, mediapackageId, channelId,
                destination));
        logger.debug("SOURCEFILE: " + source.getAbsolutePath());
        logger.debug("DESTFILE: " + destination.getAbsolutePath());
        try {
          FileSupport.link(source, destination, true);
        } catch (IOException e) {
          throw new DistributionException(format("Unable to copy %s tp %s", source, destination), e);
        }

        // Create a representation of the distributed file in the mediapackage
        MediaPackageElement distributedElement = (MediaPackageElement) element.clone();
        try {
          distributedElement.setURI(getDistributionUri(channelId,
                  (null != mediapackage.getSeries()) ? mediapackage.getSeries() : DEFAULT_NOSERIES_DIR, mediapackageId,
                  element));
        } catch (URISyntaxException e) {
          throw new DistributionException("Distributed element produces an invalid URI", e);
        }
        distributedElement.setIdentifier(null);

        logger.info(format("Finished distributing element %s@%s for publication channel %s", elementId, mediapackageId,
                channelId));
        URI uri = distributedElement.getURI();
        long now = 0L;

        // Start itbwpdk
        // If the distribution channel is ilias player
        // and the file is available locally
        // do check on file level for existence
        logger.debug("Distributionpath: " + distributionDirectory.getAbsolutePath());
        logger.debug("DistributionURI: " + uri.toString());
        // TODO: this doesn't work properly if channelID is ilias
        if ("ilias".equals(channelId) && distributionDirectory.exists()) {
          File xelement = null;
          String buildpath = "";
          boolean calc = false;
          logger.debug("URI: {}", uri.toString());
          for (String t : uri.toString().substring(iliasUrl.length() + pluginBaseURL.length()).split("/")) {
            logger.debug("URI-Part: {}", t);
            if (calc) {
              buildpath = buildpath + "/" + t;
            }
            if ("ilias".equals(t)) {
              calc = true;
            }

          }
          logger.debug("BP: " + buildpath);
          xelement = new File(distributionDirectory.getPath().concat(buildpath));
          logger.debug("XELME: " + xelement.getAbsolutePath());
          while (checkAvailability) {
            if (xelement.exists()) {
              logger.debug("Distributed file was created in download directory for ilias, " + xelement.getPath());
              break;
            }
            if (now < TIMEOUT) {
              try {
                Thread.sleep(INTERVAL);
                now += INTERVAL;
                continue;
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            }
            logger.warn("Distributed file not created in download directory for ilias, " + xelement.getPath());
            throw new DistributionException("Distributed file not created, " + xelement.getPath());
          }

        } else {
          logger.warn("Status distributed file {} could not be checked", uri);

        }
        return distributedElement;
      } catch (Exception e) {
        logger.warn("Error distributing " + element, e);
        if (e instanceof DistributionException) {
          throw (DistributionException) e;
        } else {
          throw new DistributionException(e);
        }
      }
    }
  }

  @Override
  public Job retract(String channelId, MediaPackage mediapackage, String elementId) throws DistributionException {
    notNull(mediapackage, "mediapackage");
    notNull(channelId, "channelId");
    try {
      logger.info("Create retract job for {}@{}", new Object[] { mediapackage, channelId });
      return serviceRegistry.createJob(JOB_TYPE, Operation.Retract.toString(),
              Arrays.asList(channelId, MediaPackageParser.getAsXml(mediapackage)));
    } catch (ServiceRegistryException e) {
      throw new DistributionException("Unable to create a job", e);
    }
  }

  /**
   * Retract a media package element from the distribution channel. The retracted element must not necessarily be the
   * one given as parameter <code>elementId</code>. Instead, the element's distribution URI will be calculated. This way
   * you are able to retract elements by providing the "original" element here.
   *
   * @param channelId
   *          the channel id
   * @param mediapackage
   *          the mediapackage
   * @return the retracted element or <code>null</code> if the element was not retracted
   * @throws org.opencastproject.distribution.api.DistributionException
   *           in case of an error
   */
  protected String retractElement(String channelId, MediaPackage mediapackage) throws DistributionException {
    notNull(mediapackage, "mediapackage");
    notNull(channelId, "channelId");
    String mediapackageId = mediapackage.getIdentifier().compact();
    try {
      final File mediapackageDir = getMediaPackageDirectory(channelId, mediapackage);
      logger.debug("MediaPack ageDir:" + mediapackageDir.getAbsolutePath());
      FileUtils.deleteDirectory(mediapackageDir);
      logger.info(format("Finished retracting mediapackage %s for publication channel %s", mediapackageId, channelId));
      return "done";
    } catch (Exception e) {
      logger.warn(format("Error retracting mediapackage %s for publication channel %s", mediapackageId, channelId), e);
      if (e instanceof DistributionException) {
        throw (DistributionException) e;
      } else {
        throw new DistributionException(e);
      }
    }
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.job.api.AbstractJobProducer#process(org.opencastproject.job.api.Job)
   */
  @Override
  protected String process(Job job) throws Exception {
    Operation op = null;
    String operation = job.getOperation();
    List<String> arguments = job.getArguments();
    try {
      op = Operation.valueOf(operation);
      String channelId = arguments.get(0);
      MediaPackage mediapackage = MediaPackageParser.getFromXml(arguments.get(1));
      logger.info("Calling {}", op);
      switch (op) {
        case Distribute:
          String elementId = gson.fromJson(arguments.get(2), new TypeToken<String>() {
          }.getType());
          if ('\"' == elementId.charAt(0)) {
            elementId = elementId.substring(1,elementId.length() - 1);
          }
          Boolean checkAvailability = Boolean.parseBoolean(arguments.get(3));
          MediaPackageElement distributedElement = distributeElement(channelId, mediapackage, elementId,
                  checkAvailability);
          return (distributedElement != null) ? MediaPackageElementParser.getAsXml(distributedElement) : null;
        case Retract:
          return retractElement(channelId, mediapackage);
        default:
          throw new IllegalStateException("Don't know how to handle operation '" + operation + "'");
      }
    } catch (IllegalArgumentException e) {
      throw new ServiceRegistryException("This service can't handle operations of type '" + op + "'", e);
    } catch (IndexOutOfBoundsException e) {
      throw new ServiceRegistryException("This argument list for operation '" + op + "' does not meet expectations", e);
    } catch (Exception e) {
      throw new ServiceRegistryException("Error handling operation '" + op + "'", e);
    }
  }

  /**
   * Gets the destination file to copy the contents of a mediapackage element.
   *
   * @return The file to copy the content to
   */
  protected File getDistributionFile(String channelId, MediaPackage mp, MediaPackageElement element) {
    final String uriString = element.getURI().toString();
    final String directoryName = distributionDirectory.getAbsolutePath();
    final String seriesName = (null != mp.getSeries()) ? mp.getSeries() : DEFAULT_NOSERIES_DIR;
    logger.debug("URISTRING: {}", uriString);
    if (uriString.startsWith(iliasUrl)) {
      String[] splitUrl = uriString.substring(iliasUrl.length() + pluginBaseURL.length()).split("/");
      if (splitUrl.length < 4) {
        logger.warn(format("Malformed URI %s. Must be of format .../{mediapackageId}/{elementId}/{fileName}."
                + " Trying URI without channelId", uriString));
        return new File(path(directoryName, splitUrl[0], splitUrl[1], splitUrl[2]));
      } else {
        return new File(path(directoryName, splitUrl[0], splitUrl[1], splitUrl[2], splitUrl[3]));
      }
    }
    return new File(path(directoryName, seriesName, mp.getIdentifier().compact(), element.getIdentifier(),
            FilenameUtils.getName(uriString)));
  }

  /**
   * Gets the directory containing the distributed files for this mediapackage.
   *
   * @return the filesystem directory
   */
  protected File getMediaPackageDirectory(String channelId, MediaPackage mp) {
    return new File(distributionDirectory,
            path((null != mp.getSeries()) ? mp.getSeries() : DEFAULT_NOSERIES_DIR, mp.getIdentifier().compact()));
  }

  /**
   * Gets the URI for the element to be distributed.
   *
   * @param mediaPackageId
   *          the mediapackage identifier
   * @param element
   *          The mediapackage element being distributed
   * @return The resulting URI after distribution
   * @throws URISyntaxException
   *           if the concrete implementation tries to create a malformed uri
   */
  protected URI getDistributionUri(String channelId, String seriesId, String mediaPackageId,
          MediaPackageElement element) throws URISyntaxException {
    String elementId = element.getIdentifier();
    String fileName = FilenameUtils.getName(element.getURI().toString());
    String destinationURI = UrlSupport.concat(iliasUrl, pluginBaseURL, seriesId, mediaPackageId, elementId, fileName);
    logger.debug("getDistributionURI {}", destinationURI);
    return new URI(destinationURI);
  }

  /**
   * Callback for the OSGi environment to set the workspace reference.
   *
   * @param workspace
   *          the workspace
   */
  public void setWorkspace(Workspace workspace) {
    this.workspace = workspace;
  }

  /**
   * Callback for the OSGi environment to set the service registry reference.
   *
   * @param serviceRegistry
   *          the service registry
   */
  public void setServiceRegistry(ServiceRegistry serviceRegistry) {
    this.serviceRegistry = serviceRegistry;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.job.api.AbstractJobProducer#getServiceRegistry()
   */
  @Override
  protected ServiceRegistry getServiceRegistry() {
    return serviceRegistry;
  }

  /**
   * Callback for setting the security service.
   *
   * @param securityService
   *          the securityService to set
   */
  public void setSecurityService(SecurityService securityService) {
    this.securityService = securityService;
  }

  /**
   * Callback for setting the user directory service.
   *
   * @param userDirectoryService
   *          the userDirectoryService to set
   */
  public void setUserDirectoryService(UserDirectoryService userDirectoryService) {
    this.userDirectoryService = userDirectoryService;
  }

  /**
   * Sets a reference to the organization directory service.
   *
   * @param organizationDirectory
   *          the organization directory
   */
  public void setOrganizationDirectoryService(OrganizationDirectoryService organizationDirectory) {
    this.organizationDirectoryService = organizationDirectory;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.job.api.AbstractJobProducer#getSecurityService()
   */
  @Override
  protected SecurityService getSecurityService() {
    return securityService;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.job.api.AbstractJobProducer#getUserDirectoryService()
   */
  @Override
  protected UserDirectoryService getUserDirectoryService() {
    return userDirectoryService;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.job.api.AbstractJobProducer#getOrganizationDirectoryService()
   */
  @Override
  protected OrganizationDirectoryService getOrganizationDirectoryService() {
    return organizationDirectoryService;
  }

}
