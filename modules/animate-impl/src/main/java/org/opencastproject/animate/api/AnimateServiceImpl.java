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
  private enum Operation {
    Animate
  }

  private Workspace workspace;
  private ServiceRegistry serviceRegistry;

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
  @SuppressWarnings("rawtypes")
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
    Operation op = null;
    String operation = job.getOperation();
    List<String> arguments = job.getArguments();
    try {
      op = Operation.valueOf(operation);
      MediaPackageElement inspectedElement = null;
      Map<String, String> options = null;
      switch (op) {
        case Inspect:
          URI uri = URI.create(arguments.get(0));
          options = Options.fromJson(arguments.get(1));
          inspectedElement = inspector.inspectTrack(uri, options);
          break;
        case Enrich:
          MediaPackageElement element = MediaPackageElementParser.getFromXml(arguments.get(0));
          boolean overwrite = Boolean.parseBoolean(arguments.get(1));
          options = Options.fromJson(arguments.get(2));
          inspectedElement = inspector.enrich(element, overwrite, options);
          break;
        default:
          throw new IllegalStateException("Don't know how to handle operation '" + operation + "'");
      }
      return MediaPackageElementParser.getAsXml(inspectedElement);
    } catch (IllegalArgumentException e) {
      throw new ServiceRegistryException("This service can't handle operations of type '" + op + "'", e);
    } catch (IndexOutOfBoundsException e) {
      throw new ServiceRegistryException("This argument list for operation '" + op + "' does not meet expectations", e);
    } catch (Exception e) {
      throw new ServiceRegistryException("Error handling operation '" + op + "'", e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.inspection.api.MediaInspectionService#inspect(java.net.URI)
   */
  @Override
  public Job inspect(URI uri) throws MediaInspectionException {
    return inspect(uri, Options.NO_OPTION);
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.inspection.api.MediaInspectionService#inspect(java.net.URI, java.util.Map)
   */
  @Override
  public Job inspect(URI uri, final Map<String,String> options) throws MediaInspectionException {
    assert (options != null);
    try {
      return serviceRegistry.createJob(JOB_TYPE, Operation.Inspect.toString(), Arrays.asList(uri.toString(),
              Options.toJson(options)), inspectJobLoad);
    } catch (ServiceRegistryException e) {
      throw new MediaInspectionException(e);
    }
  }

  protected void setWorkspace(Workspace workspace) {
    logger.debug("setting " + workspace);
    this.workspace = workspace;
  }

  protected void setServiceRegistry(ServiceRegistry jobManager) {
    this.serviceRegistry = jobManager;
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
}
