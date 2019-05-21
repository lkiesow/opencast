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

package org.opencastproject.monitoring;

import org.apache.karaf.features.Feature;
import org.apache.karaf.features.FeatureState;
import org.apache.karaf.features.FeaturesService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
    property = {
            "service.description=Bundle Monitoring Service"
    },
    immediate = true,
    service = BundleMonitoring.class
)
public class BundleMonitoring {

  private static final Logger logger = LoggerFactory.getLogger(BundleMonitoring.class);

  private FeaturesService featuresService;

  @Reference
  public void setFeaturesService(FeaturesService featuresService) {
    this.featuresService = featuresService;
  }

  private String bundleStateAsString(final int state) {
    switch (state) {
      case Bundle.UNINSTALLED:
        return "uninstalled;";
      case Bundle.INSTALLED:
        return "installed";
      case Bundle.RESOLVED:
        return "resolved";
      case Bundle.STARTING:
        return "starting";
      case Bundle.STOPPING:
        return "stopping";
      case Bundle.ACTIVE:
        return "active";
      default:
        throw new RuntimeException("invalid bundle state");
    }
  }

  @Activate
  public void activate(ComponentContext cc) throws Exception {
    for (Feature feature: featuresService.listFeatures()) {
      FeatureState state = featuresService.getState(feature.getId());
      logger.error("feature:{}, state:{}", feature.getName(), state);
    }

    BundleContext bundleContext = cc.getBundleContext();
    for (Bundle bundle: bundleContext.getBundles()) {
      logger.error("bundle: {}, state: {}", bundle.getSymbolicName(), bundleStateAsString(bundle.getState()));
    }
  }

}
