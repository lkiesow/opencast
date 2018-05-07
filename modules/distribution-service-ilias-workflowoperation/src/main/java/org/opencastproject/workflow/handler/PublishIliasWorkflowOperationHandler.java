/**
 *  Copyright 2009, 2010 The Regents of the University of California
 *  Licensed under the Educational Community License, Version 2.0
 *  (the "License"); you may not use this file except in compliance
 *  with the License. You may obtain a copy of the License at
 *
 *  http://www.osedu.org/licenses/ECL-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an "AS IS"
 *  BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 *  or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 *
 */
package org.opencastproject.workflow.handler;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.opencastproject.workflow.handler.IliasPublicationChannel.CHANNEL_ID;

import org.opencastproject.distribution.api.DistributionException;
import org.opencastproject.distribution.api.DistributionService;
import org.opencastproject.job.api.Job;
import org.opencastproject.job.api.JobContext;
import org.opencastproject.mediapackage.Attachment;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageElement;
import org.opencastproject.mediapackage.MediaPackageElementFlavor;
import org.opencastproject.mediapackage.MediaPackageElementParser;
import org.opencastproject.mediapackage.MediaPackageElements;
import org.opencastproject.mediapackage.MediaPackageException;
import org.opencastproject.mediapackage.MediaPackageReference;
import org.opencastproject.mediapackage.MediaPackageReferenceImpl;
import org.opencastproject.mediapackage.Publication;
import org.opencastproject.mediapackage.PublicationImpl;
import org.opencastproject.mediapackage.selector.SimpleElementSelector;
import org.opencastproject.search.api.SearchQuery;
import org.opencastproject.serviceregistry.api.ServiceRegistryException;
import org.opencastproject.util.MimeTypes;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.workflow.api.AbstractWorkflowOperationHandler;
import org.opencastproject.workflow.api.WorkflowInstance;
import org.opencastproject.workflow.api.WorkflowOperationException;
import org.opencastproject.workflow.api.WorkflowOperationInstance;
import org.opencastproject.workflow.api.WorkflowOperationResult;
import org.opencastproject.workflow.api.WorkflowOperationResult.Action;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIUtils;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The workflow definition for handling "ilias publication" operations
 */
public class PublishIliasWorkflowOperationHandler extends AbstractWorkflowOperationHandler {

  /** The logging facility */
  private static final Logger logger = LoggerFactory.getLogger(PublishIliasWorkflowOperationHandler.class);

  /** Configuration properties id */
  private static final String SERVER_URL_PROPERTY = "org.opencastproject.server.url";
  private static final String ILIASFRONTEND_URL_PROPERTY = "org.opencastproject.ilias.ui.url";
  private static final String ILIAS_URL_PROPERTY = "org.opencastproject.ilias.url";

  /** Workflow configuration option keys */
  private static final String ILIAS_SOURCE_TAGS = "ilias-source-tags";
  private static final String ILIAS_TARGET_TAGS = "ilias-target-tags";
  private static final String ILIAS_SOURCE_FLAVORS = "ilias-source-flavors";
  private static final String ILIAS_TARGET_SUBFLAVOR = "ilias-target-subflavor";

  /** Workflow configuration option keys to only merge or overwrite element in exiting mediapackage */
  private static final String OPT_MERGE_ONLY = "merge-only";

  /** The ilias distribution service */
  private DistributionService iliasDistributionService = null;

  /** The server url */
  private URL serverUrl;

  /** Whether to distribute to streaming server */
  private boolean distributeIlias = false;

  /**
   * Callback for the OSGi declarative services configuration.
   *
   * @param iliasDistributionService
   *          the ilias distribution service
   */
  protected void setIliasDistributionService(DistributionService iliasDistributionService) {
    this.iliasDistributionService = iliasDistributionService;
  }

  /** The configuration options for this handler */
  private static final SortedMap<String, String> CONFIG_OPTIONS;

  static {
    CONFIG_OPTIONS = new TreeMap<String, String>();
    CONFIG_OPTIONS.put(ILIAS_SOURCE_FLAVORS,
            "Distribute any mediapackage elements with one of these (comma separated) flavors to ilias");
    CONFIG_OPTIONS.put(ILIAS_TARGET_SUBFLAVOR,
            "Target subflavor for elements that have been distributed for ilias");
    CONFIG_OPTIONS.put(ILIAS_SOURCE_TAGS,
            "Distribute any mediapackage elements with one of these (comma separated) tags to ilias.");
    CONFIG_OPTIONS.put(ILIAS_TARGET_TAGS,
            "Add all of these comma separated tags to elements that have been distributed for ilias.");
    CONFIG_OPTIONS.put(OPT_MERGE_ONLY,
            "Republish only if it can be merged with or replace existing published data");
  }

  @Override
  protected void activate(ComponentContext cc) {
    super.activate(cc);
    BundleContext bundleContext = cc.getBundleContext();
    logger.info("Activating ILIAS WOH");


    try {
      serverUrl = new URL(bundleContext.getProperty(SERVER_URL_PROPERTY));
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException(e);
    }


    if (StringUtils.isNotBlank(bundleContext.getProperty(ILIAS_URL_PROPERTY))) {
      distributeIlias = true;
    } else {
      logger.error("{} is not set", ILIAS_URL_PROPERTY);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.workflow.api.WorkflowOperationHandler#getConfigurationOptions()
   */
  @Override
  public SortedMap<String, String> getConfigurationOptions() {
    return CONFIG_OPTIONS;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.workflow.api.WorkflowOperationHandler#start(org.opencastproject.workflow.api.WorkflowInstance,
   *      JobContext)
   */
  @Override
  public WorkflowOperationResult start(final WorkflowInstance workflowInstance, JobContext context)
          throws WorkflowOperationException {
    logger.debug("Running ilias publication workflow operation");

    MediaPackage mediaPackage = workflowInstance.getMediaPackage();
    WorkflowOperationInstance op = workflowInstance.getCurrentOperation();

    // Check which tags have been configured
    String iliasSourceTags = StringUtils.trimToEmpty(op.getConfiguration(ILIAS_SOURCE_TAGS));
    String iliasTargetTags = StringUtils.trimToEmpty(op.getConfiguration(ILIAS_TARGET_TAGS));
    String iliasSourceFlavors = StringUtils.trimToEmpty(op.getConfiguration(ILIAS_SOURCE_FLAVORS));
    String iliasTargetSubflavor = StringUtils.trimToNull(op.getConfiguration(ILIAS_TARGET_SUBFLAVOR));

    String[] sourceIliasTags = StringUtils.split(iliasSourceTags, ",");
    String[] targetIliasTags = StringUtils.split(iliasTargetTags, ",");
    String[] sourceIliasFlavors = StringUtils.split(iliasSourceFlavors, ",");

    if (sourceIliasTags.length == 0
            && sourceIliasFlavors.length == 0) {
      logger.warn("No tags or flavors have been specified, so nothing will be published to the ilias publication channel");
      return createResult(mediaPackage, Action.CONTINUE);
    }


    // Parse the ilias target flavor
    MediaPackageElementFlavor iliasSubflavor = null;
    if (iliasTargetSubflavor != null) {
      try {
        iliasSubflavor = MediaPackageElementFlavor.parseFlavor(iliasTargetSubflavor);
      } catch (IllegalArgumentException e) {
        throw new WorkflowOperationException(e);
      }
    }

    // Configure the ilias element selector
    SimpleElementSelector iliasElementSelector = new SimpleElementSelector();
    for (String flavor : sourceIliasFlavors) {
      iliasElementSelector.addFlavor(MediaPackageElementFlavor.parseFlavor(flavor));
    }
    for (String tag : sourceIliasTags) {
      iliasElementSelector.addTag(tag);
    }

    // Select the appropriate elements for ilias
    Collection<MediaPackageElement> iliasElements = iliasElementSelector.select(mediaPackage, false);

    try {
      Set<String> iliasElementIds = new HashSet<String>();

      for (MediaPackageElement elem : iliasElements) {
        logger.debug("Adding element {}", elem);
        iliasElementIds.add(elem.getIdentifier());
      }

      // Also distribute the security configuration
      // -----
      // This was removed in the meantime by a fix for MH-8515, but could now be used again.
      // -----
      Attachment[] securityAttachments = mediaPackage.getAttachments(MediaPackageElements.XACML_POLICY_SERIES);
      if (securityAttachments != null && securityAttachments.length > 0) {
        for (Attachment a : securityAttachments) {
          iliasElementIds.add(a.getIdentifier());
        }
      }

      List<Job> jobs = new ArrayList<Job>();
      try {
        if (distributeIlias) {
          for (String elementId : iliasElementIds) {
            Job job = iliasDistributionService.distribute(CHANNEL_ID, mediaPackage, elementId);
            if (job != null)
              jobs.add(job);
          }
        }
      } catch (DistributionException e) {
        throw new WorkflowOperationException(e);
      }

      if (jobs.size() < 1) {
        logger.info("No mediapackage element was found for distribution to ilias");
        return createResult(mediaPackage, Action.CONTINUE);
      }

      // Wait until all distribution jobs have returned
      if (!waitForStatus(jobs.toArray(new Job[jobs.size()])).isSuccess())
        throw new WorkflowOperationException("One of the distribution jobs did not complete successfully");

      logger.debug("Distribute of mediapackage {} completed", mediaPackage);

      String iliasUrlString = null;
      try {
        MediaPackage mediaPackageForSearch = getMediaPackageForSearchIndex(mediaPackage, jobs, iliasSubflavor, iliasElementIds, targetIliasTags);

        // MH-10216, check if only merging into existing mediapackage
        boolean merge = Boolean.parseBoolean(workflowInstance.getCurrentOperation().getConfiguration(OPT_MERGE_ONLY));
        if (merge) {
          // merge() returns merged mediapackage or null mediaPackage is not published
          mediaPackageForSearch = merge(mediaPackageForSearch);
          if (mediaPackageForSearch == null) {
            logger.info("Skipping republish for {} since it is not currently published", mediaPackage.getIdentifier().toString());
            return createResult(mediaPackage, Action.SKIP);
          }
        }

        if (!isPublishable(mediaPackageForSearch))
          throw new WorkflowOperationException("Media package does not meet criteria for publication");

        logger.info("Publishing media package {} to search index", mediaPackageForSearch);

        URL engageBaseUrl = null;
        iliasUrlString = StringUtils.trimToNull(workflowInstance.getOrganization().getProperties()
                .get(ILIASFRONTEND_URL_PROPERTY));
        if (iliasUrlString != null) {
          engageBaseUrl = new URL(iliasUrlString);
        } else {
          engageBaseUrl = serverUrl;
          logger.info(
                  "Using 'server.url' as a fallback for the non-existing organization level key '{}' for the publication url",
                  ILIASFRONTEND_URL_PROPERTY);
        }

        // Create new distribution element
        URI iliasUri = URIUtils.resolve(engageBaseUrl.toURI(), "/ilias/ui/watch.html?id="
                + mediaPackage.getIdentifier().compact());
        Publication publicationElement = PublicationImpl.publication(UUID.randomUUID().toString(), CHANNEL_ID,
                iliasUri, MimeTypes.parseMimeType("text/html"));
        mediaPackage.add(publicationElement);
        // Adding media package to the search index
        Job publishJob = null;
        try {
          System.out.println(mediaPackageForSearch);
          publishJob = iliasDistributionService.distribute(CHANNEL_ID, mediaPackageForSearch, "manifest.xml");
          if (!waitForStatus(publishJob).isSuccess()) {
            throw new WorkflowOperationException("Mediapackage " + mediaPackageForSearch.getIdentifier()
                    + " could not be published");
          }
        } catch (DistributionException e) {
          throw new WorkflowOperationException("Error publishing media package", e);
        } catch (MediaPackageException e) {
          throw new WorkflowOperationException("Error parsing media package", e);
        }

        logger.debug("Publishing of mediapackage {} completed", mediaPackage);
        return createResult(mediaPackage, Action.CONTINUE);
      } catch (MalformedURLException e) {
        logger.error("{} is malformed: {}", ILIASFRONTEND_URL_PROPERTY, iliasUrlString);
        throw new WorkflowOperationException(e);
      } catch (Throwable t) {
        if (t instanceof WorkflowOperationException)
          throw (WorkflowOperationException) t;
        else
          throw new WorkflowOperationException(t);
      }
    } catch (Exception e) {
      if (e instanceof WorkflowOperationException) {
        throw (WorkflowOperationException) e;
      } else {
        throw new WorkflowOperationException(e);
      }
    }
  }

  /**
   * Returns a mediapackage that only contains elements that are marked for distribution.
   *
   * @param current
   *          the current mediapackage
   * @param jobs
   *          the distribution jobs
   * @param streamingSubflavor
   *          flavor to be applied to elements distributed to streaming
   * @param streamingElementIds
   *          identifiers for elements that have been distributed to streaming
   * @param streamingTargetTags
   *          tags to be applied to elements distributed to streaming
   * @return the new mediapackage
   * 
   * @throws MediaPackageException when something happend //FIXME
   * @throws NotFoundException //FIXME
   * @throws ServiceRegistryException //FIXME
   * @throws WorkflowOperationException //FIXME
   */
  protected MediaPackage getMediaPackageForSearchIndex(MediaPackage current, List<Job> jobs,
          MediaPackageElementFlavor streamingSubflavor, Set<String> streamingElementIds, String[] streamingTargetTags)
          throws MediaPackageException, NotFoundException, ServiceRegistryException, WorkflowOperationException {
    MediaPackage mp = (MediaPackage) current.clone();

    // All the jobs have passed, let's update the mediapackage with references to the distributed elements
    List<String> elementsToPublish = new ArrayList<String>();
    Map<String, String> distributedElementIds = new HashMap<String, String>();

    for (Job entry : jobs) {
      Job job = serviceRegistry.getJob(entry.getId());
      String sourceElementId = job.getArguments().get(2);
      MediaPackageElement sourceElement = mp.getElementById(sourceElementId);

      // If there is no payload, then the item has not been distributed.
      if (job.getPayload() == null)
        continue;

      MediaPackageElement distributedElement = null;
      try {
        distributedElement = MediaPackageElementParser.getFromXml(job.getPayload());
      } catch (MediaPackageException e) {
        throw new WorkflowOperationException(e);
      }

      // If the job finished successfully, but returned no new element, the channel simply doesn't support this
      // kind of element. So we just keep on looping.
      if (distributedElement == null)
        continue;

      // Make sure the mediapackage is prompted to create a new identifier for this element
      distributedElement.setIdentifier(null);

      // Adjust the flavor and tags for streaming elements
      if (streamingElementIds.contains(sourceElementId)) {
        if (streamingSubflavor != null && streamingElementIds.contains(sourceElementId)) {
          MediaPackageElementFlavor flavor = sourceElement.getFlavor();
          if (flavor != null) {
            MediaPackageElementFlavor newFlavor = new MediaPackageElementFlavor(flavor.getType(),
                    streamingSubflavor.getSubtype());
            distributedElement.setFlavor(newFlavor);
          }
        }
        for (String tag : streamingTargetTags) {
          distributedElement.addTag(tag);
        }
      }

      // Copy references from the source elements to the distributed elements
      MediaPackageReference ref = sourceElement.getReference();
      if (ref != null && mp.getElementByReference(ref) != null) {
        MediaPackageReference newReference = (MediaPackageReference) ref.clone();
        distributedElement.setReference(newReference);
      }

      // Add the new element to the mediapackage
      mp.add(distributedElement);
      elementsToPublish.add(distributedElement.getIdentifier());
      distributedElementIds.put(sourceElementId, distributedElement.getIdentifier());

    }

    // Mark everything that is set for removal
    List<MediaPackageElement> removals = new ArrayList<MediaPackageElement>();
    for (MediaPackageElement element : mp.getElements()) {
      if (!elementsToPublish.contains(element.getIdentifier())) {
        removals.add(element);
      }
    }

    // Translate references to the distributed artifacts
    for (MediaPackageElement element : mp.getElements()) {

      if (removals.contains(element))
        continue;

      // Is the element referencing anything?
      MediaPackageReference reference = element.getReference();
      if (reference == null)
        continue;

      // See if the element has been distributed
      String distributedElementId = distributedElementIds.get(reference.getIdentifier());
      if (distributedElementId == null)
        continue;

      MediaPackageReference translatedReference = new MediaPackageReferenceImpl(mp.getElementById(distributedElementId));
      if (reference.getProperties() != null) {
        translatedReference.getProperties().putAll(reference.getProperties());
      }

      // Set the new reference
      element.setReference(translatedReference);

    }

    // Remove everything we don't want to add to publish
    for (MediaPackageElement element : removals) {
      mp.remove(element);
    }
    return mp;
  }

  /** Media package must meet these criteria in order to be published. */
  private boolean isPublishable(MediaPackage mp) {
    boolean hasTitle = !isBlank(mp.getTitle());
    if (!hasTitle)
      logger.warn("Media package does not meet criteria for publication: There is no title");

    boolean hasTracks = mp.hasTracks();
    if (!hasTracks)
      logger.warn("Media package does not meet criteria for publication: There are no tracks");

    return hasTitle && hasTracks;
  }

  /**
   * MH-10216, method copied from the original RepublishWorkflowOperationHandler
   * Merges mediapackage with published mediapackage.
   *
   * @param mediaPackageForSearch //FIXME
   * @return merged mediapackage or null if a published medipackage was not found
   * @throws WorkflowOperationException //FIXME
   */
  protected MediaPackage merge(MediaPackage mediaPackageForSearch) throws WorkflowOperationException {
    MediaPackage mergedMediaPackage = null;
      SearchQuery query = new SearchQuery().withId(mediaPackageForSearch.toString());
      query.includeEpisodes(true);
      query.includeSeries(false);
   /*   SearchResult result = searchService.getByQuery(query);
      if (result.size() == 0) {
        logger.info("The search service doesn't know mediapackage {}, cannot be republished.", mediaPackageForSearch);
        return mergedMediaPackage; // i.e. null
      } else if (result.size() > 1) {
        logger.warn("More than one mediapackage with id {} returned from search service", mediaPackageForSearch);
        throw new WorkflowOperationException("More than one mediapackage with id " + mediaPackageForSearch + " found");
      } else {
        // else, merge the new with the existing (new elements will overwrite existing elements)
        mergedMediaPackage = mergePackages(mediaPackageForSearch, result.getItems()[0].getMediaPackage());
      }*/

    return mergedMediaPackage;
  }

  /**
   * MH-10216, Copied from the original RepublishWorkflowOperationHandler
   *
   * Merges the updated mediapackage with the one that is currently published in a way where the updated elements
   * replace existing ones in the published mediapackage based on their flavor.
   * <p>
   * If <code>publishedMp</code> is <code>null</code>, this method returns the updated mediapackage without any
   * modifications.
   *
   * @param updatedMp
   *          the updated media package
   * @param publishedMp
   *          the mediapackage that is currently published
   * @return the merged mediapackage
   */
  protected MediaPackage mergePackages(MediaPackage updatedMp, MediaPackage publishedMp) {
    if (publishedMp == null)
      return updatedMp;

    MediaPackage mergedMediaPackage = (MediaPackage) updatedMp.clone();
    for (MediaPackageElement element : publishedMp.elements()) {
      String type = element.getElementType().toString().toLowerCase();
      if (updatedMp.getElementsByFlavor(element.getFlavor()).length == 0) {
        logger.info("Merging {} '{}' into the updated mediapackage", type, element.getIdentifier());
        mergedMediaPackage.add((MediaPackageElement) element.clone());
      } else {
        logger.info(String.format("Overwriting existing %s '%s' with '%s' in the updated mediapackage",
          type, element.getIdentifier(), updatedMp.getElementsByFlavor(element.getFlavor())[0].getIdentifier()));

      }
    }

    return mergedMediaPackage;
  }

}
