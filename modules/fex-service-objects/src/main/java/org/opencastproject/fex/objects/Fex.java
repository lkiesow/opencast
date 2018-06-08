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
package org.opencastproject.fex.objects;

import org.json.simple.JSONObject;

public class Fex {
  protected String fexId;

  protected String seriesId;

  protected String lectureId;

  protected String receiver;

  protected boolean sbs;

  public Fex() {

  }

  public static String toJson(Fex fex) {

    try {
      JSONObject jsonobj = new JSONObject();
      jsonobj.put("fexId", fex.getFexId());
      jsonobj.put("seriesId", fex.getSeriesId());
      jsonobj.put("lectureId", fex.getLectureId());
      jsonobj.put("receiver", fex.getReceiver());
      jsonobj.put("sbs", fex.isSbs());
      return jsonobj.toJSONString();
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  public String getFexId() {
    return fexId;
  }

  public void setFexId(String fexId) {
    if (fexId == null) {
      throw new IllegalArgumentException("Fex ID can't be null");
    }
    if (fexId.length() > 128) {
      throw new IllegalArgumentException("Fex ID can't be longer than 128 characters");
    }
    this.fexId = fexId;
  }

  public String getSeriesId() {
    return seriesId;
  }

  public void setSeriesId(String seriesId) {
    if (seriesId == null) {
      throw new IllegalArgumentException("Series ID can't be null");
    }
    if (seriesId.length() > 128) {
      throw new IllegalArgumentException("Series ID can't be longer than 128 characters");
    }
    this.seriesId = seriesId;
  }

  public String getLectureId() {
    return lectureId;
  }

  public void setLectureId(String lectureId) {
    this.lectureId = lectureId;
  }

  public String getReceiver() {
    return receiver;
  }

  public void setReceiver(String receiver) {
    this.receiver = receiver;
  }

  public boolean isSbs() {
    return sbs;
  }

  public void setSbs(boolean sbs) {
    this.sbs = sbs;
  }
}
