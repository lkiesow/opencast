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
package org.opencastproject.workflow.handler.workflow;

import org.opencastproject.job.api.JobContext;
import org.opencastproject.mediapackage.Catalog;
import org.opencastproject.mediapackage.EName;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageElementFlavor;
import org.opencastproject.metadata.dublincore.DublinCoreCatalog;
import org.opencastproject.metadata.dublincore.DublinCoreUtil;
import org.opencastproject.metadata.dublincore.DublinCoreValue;
import org.opencastproject.util.XmlNamespaceContext;
import org.opencastproject.workflow.api.AbstractWorkflowOperationHandler;
import org.opencastproject.workflow.api.WorkflowInstance;
import org.opencastproject.workflow.api.WorkflowOperationException;
import org.opencastproject.workflow.api.WorkflowOperationResult;
import org.opencastproject.workflow.api.WorkflowOperationResult.Action;
import org.opencastproject.workspace.api.Workspace;

import com.entwinemedia.fn.Fn;
import com.entwinemedia.fn.P1Lazy;
import com.entwinemedia.fn.data.Opt;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

// TODO JavaDoc
// TODO Rename to something like `MapMetadataWOH`?
// TODO Change the package when you bundle this
// TODO Error handling
// TODO Proper logging
// TODO Do you have to register the configuration somehow?
//   There is a method you can overload
// TODO Write tests
// TODO Clean up "IDE spaces"
// TODO Add comments
// TODO Do you have to do cleanup?
//   When exceptions fly?

/**
 * The workflow definition for handling "transfer-metadata" operations
 */
public class TransferMetadataWorkflowOperationHandler extends AbstractWorkflowOperationHandler {

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.workflow.api.WorkflowOperationHandler#start(org.opencastproject.workflow.api.WorkflowInstance,
   *      JobContext)
   */
  @Override
  public WorkflowOperationResult start(final WorkflowInstance workflowInstance, JobContext context)
          throws WorkflowOperationException {
    logger.debug("Running transfer-metadata workflow operation");

    MediaPackage mediaPackage = workflowInstance.getMediaPackage();
    Configuration configuration = new Configuration(workflowInstance);

    // TODO I hate this pattern
    Metadata sourceMetadata;
    try {
      sourceMetadata = new Metadata(mediaPackage, configuration.sourceFlavor);
    } catch (Metadata.NoMetadataFoundException e) {
      // TODO Log?
      return createResult(mediaPackage, Action.SKIP);
    }
    Metadata targetMetadata;
    try {
      targetMetadata = new Metadata(mediaPackage, configuration.targetFlavor);
    } catch (Metadata.NoMetadataFoundException e) {
      throw new WorkflowOperationException(e);
    }

    // TODO Rename field or element
    if (targetMetadata.dcCatalog.get(configuration.targetElement).size() > 0 && !configuration.force) {
      throw new WorkflowOperationException("The target metadata field already exists and forcing was not configured");
    }
    // TODO Does this do the right thing when the field does not exist in the source?
    // TODO This is a hack because extended metadata fields with multiple values don't work at the moment
    configuration.concatDelimiter.fold(new Fn<String, Void>() {
      @Override
      public Void apply(String concatDelimiter) {
        targetMetadata.dcCatalog.set(configuration.targetElement,
                sourceMetadata.dcCatalog.get(configuration.sourceElement).stream()
                        .map(DublinCoreValue::getValue)
                        .collect(Collectors.joining(concatDelimiter)));
        return null;
      }
    }, new P1Lazy<Void>() {
      @Override
      public Void get1() {
        targetMetadata.dcCatalog.set(configuration.targetElement,
                sourceMetadata.dcCatalog.get(configuration.sourceElement));
        return null;
      }
    });

    // TODO Maybe it's a bad idea to reach into these `Metadata` objects like this
    // TODO It is shitty that we even have to provide the prefix twice
    //   Actually this should just be written as configured evem when there are no fields
    for (String targetPrefix: configuration.targetPrefix) {
      targetMetadata.dcCatalog.addBindings(XmlNamespaceContext.mk(targetPrefix,
              configuration.targetElement.getNamespaceURI()));
    }
    try {
      targetMetadata.save();
    } catch (IOException e) {
      throw new WorkflowOperationException(e);
    }

    return createResult(mediaPackage, Action.CONTINUE);
  }

  private class Metadata {
    private final MediaPackage mediaPackage;
    private final Catalog catalog;
    private final DublinCoreCatalog dcCatalog;

    // TODO I don't like that this constructor does multiple things ...
    Metadata(MediaPackage mediaPackage, MediaPackageElementFlavor flavor) throws NoMetadataFoundException {
      this.mediaPackage = mediaPackage;

      Catalog[] catalogs = mediaPackage.getCatalogs(flavor);
      if (catalogs.length < 1) {
        throw new NoMetadataFoundException();
      } else {
        if (catalogs.length > 1) {
          // TODO Is "using the first one" even right?
          //   Who determines this order?
          logger.warn("More than one metadata dcCatalog of flavor {} found; using the first one", flavor);
        }
        catalog = catalogs[0];
        // TODO Error handling?!
        dcCatalog = DublinCoreUtil.loadDublinCore(workspace, catalogs[0]);
      }
    }

    // TODO What about localization?!

    public void save() throws IOException {
      // TODO Error handling
      String filename = FilenameUtils.getName(catalog.getURI().toString());
      InputStream stream = IOUtils.toInputStream(dcCatalog.toXmlString(), StandardCharsets.UTF_8);
      URI newCatalogURI = workspace.put(mediaPackage.getIdentifier().toString(), catalog.getIdentifier(), filename,
              stream);
      catalog.setURI(newCatalogURI);
      catalog.setChecksum(null);
    }

    // TODO Also do you want nested classes this much?
    //   Maybe split the whole thing up in the end
    class NoMetadataFoundException extends Exception {
    }
  }

  // TODO Forcing!
  private class Configuration {
    private final MediaPackageElementFlavor sourceFlavor;
    private final MediaPackageElementFlavor targetFlavor;
    private final EName sourceElement;
    private final EName targetElement;
    private final boolean force;
    private final Opt<String> concatDelimiter;
    private final Opt<String> targetPrefix;

    Configuration(WorkflowInstance workflowInstance)
            throws WorkflowOperationException {
      // TODO What if stuff is not found?
      sourceFlavor = MediaPackageElementFlavor.parseFlavor(getConfig(workflowInstance, "source-flavor"));
      // TODO Maybe default the target to the source?
      targetFlavor = MediaPackageElementFlavor.parseFlavor(getConfig(workflowInstance, "target-flavor"));
      sourceElement = EName.fromString(getConfig(workflowInstance, "source-element"));
      targetElement = EName.fromString(getConfig(workflowInstance, "target-element"));
      force = getOptConfig(workflowInstance, "force").isSome();
      concatDelimiter = getOptConfig(workflowInstance, "concat");
      targetPrefix = getOptConfig(workflowInstance, "target-prefix");
    }
  }

  /** Reference to the workspace */
  private Workspace workspace = null;

  /** OSGi callback to inject the workspace */
  public void setWorkspace(Workspace workspace) {
    this.workspace = workspace;
  }

  /** The logging facility */
  private static final Logger logger = LoggerFactory.getLogger(TransferMetadataWorkflowOperationHandler.class);
}
