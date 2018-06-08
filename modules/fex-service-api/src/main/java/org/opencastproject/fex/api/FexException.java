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
package org.opencastproject.fex.api;

/**
 * Created by ac129583 on 8/18/17.
 */

/**
 * Exception thrown in Fex Service
 */
public class FexException extends Exception {
  /**
   * UUID of exception
   */
  private static final long serialVersionUID = -4435449067684730731L;

  /**
   * Used to create exception without parameters.
   */
  public FexException() {
  }

  /**
   * Used to create exception with exception message.
   *
   * @param message exception message
   */
  public FexException(String message) {
    super(message);
  }

  /**
   * Used to create exception with a cause.
   *
   * @param cause
   */
  public FexException(Throwable cause) {
    super(cause);
  }

  /**
   * Used to create exception with a message and a cause
   *
   * @param message
   * @param cause
   */
  public FexException(String message, Throwable cause) {
    super(message, cause);
  }

}
