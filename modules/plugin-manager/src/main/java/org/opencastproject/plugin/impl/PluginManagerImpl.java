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

package org.opencastproject.plugin.impl;

import org.apache.karaf.features.Feature;
import org.apache.karaf.features.FeaturesService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple tutorial class to learn about Opencast Services
 */
@Component(
  property = {
    "service.description=Plugin Manager Service"
  },
  immediate = true,
  service = PluginManagerImpl.class
)
public class PluginManagerImpl {

  /** The module specific logger */
  private static final Logger logger = LoggerFactory.getLogger(PluginManagerImpl.class);

  private FeaturesService featuresService;

  @Reference
  public void setFeaturesService(FeaturesService featuresService) {
    this.featuresService = featuresService;
  }

  @Activate
  void activate() throws Exception {
    logger.debug("Activating {}", PluginManagerImpl.class);
    for (Feature feature: featuresService.listFeatures()) {
      if (feature.getName().startsWith("opencast")) {
        logger.error("feature {}, name {}, install {}", feature, feature.getName(), feature.getInstall());
        if (feature.getName().equals("opencast-moodle")) {
          logger.warn("installing {}", feature);
        }
      }
    }
    featuresService.installFeature("opencast-moodle/9.0.0.SNAPSHOT");
  }

}
