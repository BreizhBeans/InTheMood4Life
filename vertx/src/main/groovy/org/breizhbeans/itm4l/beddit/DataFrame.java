/*
 * Copyright (C) 2017 Lambour Sebastien
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.breizhbeans.itm4l.beddit;

public class DataFrame {
 private long timestamp;
 private String frame;

 public DataFrame(long timestamp, String frame) {
  this.timestamp = timestamp;
  this.frame = frame;
 }

 public long getTimestamp() {
  return timestamp;
 }

 public void setTimestamp(long timestamp) {
  this.timestamp = timestamp;
 }

 public String getFrame() {
  return frame;
 }

 public void setFrame(String frame) {
  this.frame = frame;
 }

}
