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

import static java.lang.String.format;

import org.opencastproject.job.api.Job;
import org.opencastproject.job.api.JobContext;
import org.opencastproject.mediapackage.Catalog;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageElement;
import org.opencastproject.mediapackage.MediaPackageElementBuilderFactory;
import org.opencastproject.mediapackage.MediaPackageElementFlavor;
import org.opencastproject.mediapackage.MediaPackageElementParser;
import org.opencastproject.mediapackage.MediaPackageException;
import org.opencastproject.mediapackage.Track;
import org.opencastproject.mediapackage.selector.SimpleElementSelector;
import org.opencastproject.mediapackage.selector.TrackSelector;
import org.opencastproject.smil.api.SmilException;
import org.opencastproject.smil.api.SmilResponse;
import org.opencastproject.smil.api.SmilService;
import org.opencastproject.smil.entity.api.Smil;
import org.opencastproject.smil.entity.media.api.SmilMediaObject;
import org.opencastproject.smil.entity.media.container.api.SmilMediaContainer;
import org.opencastproject.smil.entity.media.element.api.SmilMediaElement;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.videoeditor.api.ProcessFailedException;
import org.opencastproject.videoeditor.api.VideoEditorService;
import org.opencastproject.workflow.api.AbstractWorkflowOperationHandler;
import org.opencastproject.workflow.api.WorkflowInstance;
import org.opencastproject.workflow.api.WorkflowOperationException;
import org.opencastproject.workflow.api.WorkflowOperationInstance;
import org.opencastproject.workflow.api.WorkflowOperationResult;
import org.opencastproject.workflow.api.WorkflowOperationResult.Action;
import org.opencastproject.workspace.api.Workspace;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.Collection;
import java.util.List;

import javax.xml.bind.JAXBException;

public class VideoEditorWorkflowOperationHandler extends AbstractWorkflowOperationHandler {

  private static final Logger logger = LoggerFactory.getLogger(VideoEditorWorkflowOperationHandler.class);

  /** Name of the configuration option that provides the source flavors we use for processing. */
  private static final String SOURCE_FLAVORS_PROPERTY = "source-flavors";

  /** Bypasses video editor's encoding operation but keep the raw smil for later processing */
  private static final String SKIP_PROCESSING_PROPERTY = "skip-processing";

  /** Name of the configuration option that provides the SMIL flavor as input. */
  private static final String SOURCE_SMIL_FLAVOR_PROPERTY = "source-smil-flavors";

  /** Name of the configuration option that provides the SMIL flavor as input. */
  private static final String TARGET_SMIL_FLAVOR_PROPERTY = "target-smil-flavor";

  /** Name of the configuration that provides the target flavor subtype for encoded media tracks. */
  private static final String TARGET_FLAVOR_SUBTYPE_PROPERTY = "target-flavor-subtype";

  /** Name of the configuration that provides the SMIL file name */
  private static final String SMIL_FILE_NAME = "smil.smil";

  /**
   * Name of the configuration that controls whether or not to process the input video(s) even when there are no
   * trimming points
   */
  private static final String SKIP_NOT_TRIMMED_PROPERTY = "skip-if-not-trimmed";

  /**
   * The SMIL service to modify SMIL files.
   */
  private SmilService smilService;
  /**
   * The VideoEditor service to edit files.
   */
  private VideoEditorService videoEditorService;
  /**
   * The workspace.
   */
  private Workspace workspace;

  class Config {
    protected String sourceSmilFlavor;
    protected String targetSmilFlavor;
    protected String sourceFlavors;
    protected String targetSubFlavor;
    protected boolean skipProcessing;
    protected boolean skipIfNoTrim;

    Config(final WorkflowOperationInstance operation) throws WorkflowOperationException {
      sourceSmilFlavor = operation.getConfiguration(SOURCE_SMIL_FLAVOR_PROPERTY);
      targetSmilFlavor = operation.getConfiguration(TARGET_SMIL_FLAVOR_PROPERTY);
      sourceFlavors = operation.getConfiguration(SOURCE_FLAVORS_PROPERTY);
      targetSubFlavor = operation.getConfiguration(TARGET_FLAVOR_SUBTYPE_PROPERTY);
      skipProcessing = BooleanUtils.toBoolean(operation.getConfiguration(SKIP_PROCESSING_PROPERTY));
      skipIfNoTrim = BooleanUtils.toBoolean(operation.getConfiguration(SKIP_NOT_TRIMMED_PROPERTY));

      if (StringUtils.isEmpty(sourceSmilFlavor)) {
        throw new WorkflowOperationException(format("Property %s not set", SOURCE_SMIL_FLAVOR_PROPERTY));
      }
      if (StringUtils.isEmpty(targetSmilFlavor)) {
        throw new WorkflowOperationException(format("Property %s not set", TARGET_SMIL_FLAVOR_PROPERTY));
      }
      if (StringUtils.isEmpty(sourceFlavors)) {
        throw new WorkflowOperationException(format("%s not configured.", SOURCE_FLAVORS_PROPERTY));
      }
      if (StringUtils.isEmpty(targetSubFlavor) && !skipProcessing) {
        throw new WorkflowOperationException(format("%s is not configured", TARGET_FLAVOR_SUBTYPE_PROPERTY));
      }
    }
  }

  private Collection<MediaPackageElement> getElementsByFlavor(final MediaPackage mediaPackage, final String flavors) {
    final SimpleElementSelector elementSelector = new SimpleElementSelector();
    elementSelector.addFlavors(flavors);
    return elementSelector.select(mediaPackage, false);
  }

  private Collection<Track> getSourceTracks(final Config config, final MediaPackage mediaPackage) {
    TrackSelector trackSelector = new TrackSelector();
    trackSelector.addFlavors(config.sourceFlavors);
    return trackSelector.select(mediaPackage, false);
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.workflow.api.WorkflowOperationHandler#start(org.opencastproject.workflow.api.WorkflowInstance,
   *      JobContext)
   */
  @Override
  public WorkflowOperationResult start(WorkflowInstance workflowInstance, JobContext context)
          throws WorkflowOperationException {

    final MediaPackage mediaPackage = workflowInstance.getMediaPackage();
    final WorkflowOperationInstance operation = workflowInstance.getCurrentOperation();
    logger.info("Start {} on media package {}", operation.getId(), mediaPackage.getIdentifier());

    final Config config = new Config(operation);

    // Check at least one SMIL catalog exists
    Collection<MediaPackageElement> smilCatalogs = getElementsByFlavor(mediaPackage, config.sourceSmilFlavor);
    if (smilCatalogs.isEmpty()) {
      logger.info("Skipping cutting operation since no edit decision list is available");
      return skip(operation, mediaPackage, config);
    }

    // Get source tracks
    Collection<Track> sourceTracks = getSourceTracks(config, mediaPackage);
    if (sourceTracks.isEmpty()) {
      throw new WorkflowOperationException(format("No source tracks with flavors %s found in media package %s.",
              config.sourceFlavors, mediaPackage.getIdentifier()));
    }

    // Load source SMIL file and update the tracks
    Smil smil;
    MediaPackageElement smilCatalog = smilCatalogs.iterator().next();
    try {
      final File smilFile = workspace.get(smilCatalog.getURI());
      smil = smilService.fromXml(smilFile).getSmil();
      smil = replaceAllTracksWith(smil, sourceTracks.toArray(new Track[0]));
    } catch (NotFoundException | IOException ex) {
      throw new WorkflowOperationException(format("Can't open SMIL catalog %s from media package %s.",
          smilCatalog.getIdentifier(), mediaPackage.getIdentifier().compact()), ex);
    } catch (SmilException ex) {
      throw new WorkflowOperationException(ex);
    }

    // Generate target smil
    if (StringUtils.isNotEmpty(config.targetSmilFlavor)) {
      final MediaPackageElementFlavor targetSmilFlavor = MediaPackageElementFlavor.parseFlavor(config.targetSmilFlavor);
      try (InputStream is = IOUtils.toInputStream(smil.toXML(), "UTF-8")) {
        // put modified SMIL into workspace
        final URI newSmilUri = workspace.put(mediaPackage.getIdentifier().compact(), smil.getId(), SMIL_FILE_NAME, is);
        final Catalog catalog = (Catalog) MediaPackageElementBuilderFactory.newInstance().newElementBuilder()
            .elementFromURI(newSmilUri, MediaPackageElement.Type.Catalog, targetSmilFlavor);
        catalog.setIdentifier(null);
        mediaPackage.addDerived(catalog, smilCatalog);
      } catch (Exception ex) {
        throw new WorkflowOperationException("Error generating target SMIL", ex);
      }
    }

    // If skipProcessing, The track is processed by a separate operation which takes the SMIL file and encode directly
    // to delivery format
    if (config.skipProcessing) {
      logger.info("Editor workflow {} finished - smil file is {}", workflowInstance.getId(), smil.getId());
      return createResult(mediaPackage, Action.CONTINUE);
    }

    // Check if we need to trim/process the video based on the input SMIL
    if (config.skipIfNoTrim && !isTrimmingNecessary(mediaPackage, smil)) {
      return skip(workflowInstance, context);
    }

    // Create video edit jobs and run them
    List<Job> jobs;
    try {
      logger.info("Create processing jobs for SMIL file: {}", smilCatalog.getIdentifier());
      jobs = videoEditorService.processSmil(smil);
      if (!waitForStatus(jobs.toArray(new Job[0])).isSuccess()) {
        throw new WorkflowOperationException(format("Processing SMIL file failed: %s", smilCatalog.getIdentifier()));
      }
      logger.info("Finished processing of SMIL file: {}", smilCatalog.getIdentifier());
    } catch (ProcessFailedException ex) {
      throw new WorkflowOperationException(format("Processing SMIL failed: %s", smilCatalog.getIdentifier()), ex);
    }

    // Move edited tracks to work location and set target flavor
    boolean mpAdded = false;
    for (Job job : jobs) {
      try {
        final Track editedTrack = (Track) MediaPackageElementParser.getFromXml(job.getPayload());
        final MediaPackageElementFlavor flavor = editedTrack.getFlavor();
        editedTrack.setFlavor(new MediaPackageElementFlavor(flavor.getType(), config.targetSubFlavor));
        final URI editedTrackNewUri = workspace.moveTo(editedTrack.getURI(), mediaPackage.getIdentifier().compact(),
                editedTrack.getIdentifier(), FilenameUtils.getName(editedTrack.getURI().toString()));
        editedTrack.setURI(editedTrackNewUri);
        for (Track track : sourceTracks) {
          if (track.getFlavor().getType().equals(flavor.getType())) {
            mediaPackage.addDerived(editedTrack, track);
            mpAdded = true;
            break;
          }
        }

        if (!mpAdded) {
          mediaPackage.add(editedTrack);
        }

      } catch (MediaPackageException ex) {
        throw new WorkflowOperationException("Failed to get information about the edited track(s)", ex);
      } catch (NotFoundException | IOException | IllegalArgumentException ex) {
        throw new WorkflowOperationException("Moving edited track to work location failed.", ex);
      } catch (Exception ex) {
        throw new WorkflowOperationException(ex);
      }
    }

    logger.info("Operation `editor` {} finished", workflowInstance.getId());
    return createResult(mediaPackage, Action.CONTINUE);
  }

  /**
   * Check if there are trimming points defined or if there is just one segment covering all of the media track in
   * which case cutting is not necessary at all.
   *
   * TODO: The SmilService implementation does not do any filtering or optimizations for us. We need to process the
   *       SMIL file ourselves. The SmilService should be something more than a bunch of classes encapsulating data
   *       types which provide no extra functionality (e.g. we shouldn't have to check the SMIL structure ourselves)
   *
   * @param mediaPackage
   *        The media package used for video editting
   * @param smil
   *        SMIL file with cutting information
   * @return if trimming is necessary.
   */
  private boolean isTrimmingNecessary(final MediaPackage mediaPackage, final Smil smil) {
    // We should not modify the SMIL file as we traverse through its elements, so we make a copy and modify it instead
    try {
      Smil filteredSmil = smilService.fromXml(smil.toXML()).getSmil();
      for (SmilMediaObject element : smil.getBody().getMediaElements()) {
        // body should contain par elements
        if (element.isContainer()) {
          SmilMediaContainer container = (SmilMediaContainer) element;
          if (SmilMediaContainer.ContainerType.PAR == container.getContainerType()) {
            continue;
          }
        }
        filteredSmil = smilService.removeSmilElement(filteredSmil, element.getId()).getSmil();
      }

      // Return an empty job list if not PAR components (i.e. trimming points) are defined, or if there is just
      // one that takes the whole video size
      switch (filteredSmil.getBody().getMediaElements().size()) {
        case 0:
          logger.info("Skipping editor for '{}' because the SMIL does not define any trimming points",
              mediaPackage.getIdentifier());
          return false;

        case 1:
          // If the whole duration was not defined in the media package, we cannot tell whether or not this PAR
          // component represents the whole duration or not, therefore we don't bother to try
          if (mediaPackage.getDuration() < 0) {
            return true;
          }

          SmilMediaContainer parElement = (SmilMediaContainer) filteredSmil.getBody().getMediaElements().get(0);
          for (SmilMediaObject elementChild : parElement.getElements()) {
            if (!elementChild.isContainer()) {
              SmilMediaElement media = (SmilMediaElement) elementChild;
              // Compare begin and endpoints
              // If they don't represent the whole length, then we break --we have a trimming point
              if ((media.getClipBeginMS() != 0) || (media.getClipEndMS() != mediaPackage.getDuration())) {
                return true;
              }
            }
          }

          logger.info("Skipping editor for '{}' because the trimming points in the SMIL correspond to the beginning "
              + "and the end of the video", mediaPackage.getIdentifier());
          return false;

        default:
          break;
      }
    } catch (MalformedURLException | SmilException | JAXBException | SAXException e) {
      logger.warn("Error parsing input SMIL to determine if it has trimpoints. "
          + "We will assume it does and go on creating jobs.");
    }
    return true;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.workflow.api.AbstractWorkflowOperationHandler#skip(org.opencastproject.workflow.api.WorkflowInstance,
   *      JobContext)
   */
  private WorkflowOperationResult skip(final WorkflowOperationInstance operation, final MediaPackage mediaPackage,
      final Config config) throws WorkflowOperationException {
    logger.info("Skip video editor operation for {}", mediaPackage.getIdentifier());

    // break if we do not need to do any further processing
    if (config.skipProcessing) {
      return createResult(mediaPackage, Action.SKIP);
    }

    // Get source tracks
    Collection<Track> sourceTracks = getSourceTracks(config, mediaPackage);

    for (Track sourceTrack : sourceTracks) {
      // Set target track flavor
      final Track clonedTrack = (Track) sourceTrack.clone();
      clonedTrack.setIdentifier(null);
      // Use the same URI as the original
      clonedTrack.setURI(sourceTrack.getURI());
      clonedTrack.setFlavor(new MediaPackageElementFlavor(sourceTrack.getFlavor().getType(), config.targetSubFlavor));
      mediaPackage.addDerived(clonedTrack, sourceTrack);
    }

    return createResult(mediaPackage, Action.SKIP);
  }

  private Smil replaceAllTracksWith(Smil smil, Track[] otherTracks) throws SmilException {
    SmilResponse smilResponse;
    try {
      // copy SMIL to work with
      smilResponse = smilService.fromXml(smil.toXML());
    } catch (Exception ex) {
      throw new SmilException("Can not parse SMIL files.");
    }

    long start;
    long end;
    boolean hasElements = false; // Check for missing smil so the process will fail early if no tracks found
    // iterate over all elements inside SMIL body
    for (SmilMediaObject elem : smil.getBody().getMediaElements()) {
      start = -1L;
      end = -1L;
      // body should contain par elements (container)
      if (elem.isContainer()) {
        // iterate over all elements in container
        for (SmilMediaObject child : ((SmilMediaContainer) elem).getElements()) {
          // second depth should contain media elements like audio or video
          if (!child.isContainer() && child instanceof SmilMediaElement) {
            SmilMediaElement media = (SmilMediaElement) child;
            start = media.getClipBeginMS();
            end = media.getClipEndMS();
            // remove it
            smilResponse = smilService.removeSmilElement(smilResponse.getSmil(), media.getId());
            hasElements = true;
          }
        }
        if (start != -1L && end != -1L) {
          // add the new tracks inside
          smilResponse = smilService.addClips(smilResponse.getSmil(), elem.getId(), otherTracks, start, end - start);
        }
      } else if (elem instanceof SmilMediaElement) {
        throw new SmilException("Media elements inside SMIL body are not supported.");
      }
    }
    if (!hasElements) {
      throw new SmilException("Smil does not define any elements");
    }
    return smilResponse.getSmil();
  }

  public void setSmilService(SmilService smilService) {
    this.smilService = smilService;
  }

  public void setVideoEditorService(VideoEditorService editor) {
    videoEditorService = editor;
  }

  public void setWorkspace(Workspace workspace) {
    this.workspace = workspace;
  }
}
