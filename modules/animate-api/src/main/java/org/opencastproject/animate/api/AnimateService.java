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

package org.opencastproject.animate.api;

import org.opencastproject.job.api.Job;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Generate animated video sequences.
 */
public interface AnimateService {

  /**
   * The namespace distinguishing animation jobs from other types
   */
  String JOB_TYPE = "org.opencastproject.animate";

  Job animate(File animation, Map<String, String> metadata) throws IOException;
}
