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

import org.apache.commons.lang3.StringUtils;

/**
 * Query object used for storing search parameters.
 */
public class FexQuery {
  /**
   * Maximum number of results returned
   */
  protected int count;
  /**
   * start page number
   */
  protected int startPage;
  /**
   * Free text search
   */
  protected String text;
  /**
   * Fex id search
   */
  protected String fexId;
  /**
   * Series id search
   */
  protected String seriesId;
  /**
   * Receiver search
   */
  protected String receiver;
  /**
   * Lecture id search
   */
  protected String lectureId;
  /**
   * organization search
   */
  protected String organization;
  /**
   * Sbs search
   */
  protected boolean sbs;

  /**
   * Get result count
   *
   * @return
   */
  public int getCount() {
    return count;
  }

  /**
   * Set maximum number of results
   *
   * @param count
   * @return
   */
  public FexQuery setCount(int count) {
    this.count = count;
    return this;
  }

  /**
   * Get start page
   *
   * @return
   */
  public int getStartPage() {
    return startPage;
  }

  /**
   * Set start page
   *
   * @param startPage
   * @return
   */
  public FexQuery setStartPage(int startPage) {
    this.startPage = startPage;
    return this;
  }

  /**
   * Get text
   *
   * @return
   */
  public String getText() {
    return text;
  }

  /**
   * Set search over all text fields
   *
   * @param text
   * @return
   */
  public FexQuery setText(String text) {
    if (StringUtils.isNotBlank(text)) {
      this.text = text;
    }
    return this;
  }

  /**
   * Get fex id
   *
   * @return
   */
  public String getFexId() {
    return fexId;
  }

  /**
   * Set search by fex id
   *
   * @param fexId
   * @return
   */
  public FexQuery setFexId(String fexId) {
    if (StringUtils.isNotBlank(fexId)) {
      this.fexId = fexId;
    }
    return this;
  }

  /**
   * Get series id
   *
   * @return
   */
  public String getSeriesId() {
    return seriesId;
  }

  /**
   * Set search by series id
   *
   * @param seriesId
   * @return
   */
  public FexQuery setSeriesId(String seriesId) {
    this.seriesId = seriesId;
    return this;
  }

  /**
   * Get organization
   *
   * @return
   */
  public String getOrganization() {
    return organization;
  }

  /**
   * Set search by organization
   *
   * @param organization
   * @return
   */
  public FexQuery setOrganization(String organization) {
    this.organization = organization;
    return this;
  }

  /**
   * Get receiver
   *
   * @return
   */
  public String getReceiver() {
    return receiver;
  }

  /**
   * Set search by receiver
   *
   * @param receiver
   * @return
   */
  public FexQuery setReceiver(String receiver) {
    this.receiver = receiver;
    return this;
  }

  /**
   * Get lecture id
   *
   * @return
   */
  public String getLectureId() {
    return lectureId;
  }

  /**
   * Set search by lecture id
   *
   * @param lectureId
   * @return
   */
  public FexQuery setLectureId(String lectureId) {
    this.lectureId = lectureId;
    return this;
  }

  /**
   * Get sbs
   *
   * @return
   */
  public boolean isSbs() {
    return sbs;
  }

  /**
   * Set search by sbs
   *
   * @param sbs
   * @return
   */
  public FexQuery setSbs(boolean sbs) {
    this.sbs = sbs;
    return this;
  }
}
