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
package org.breizhbeans.itm4l.parameters

import groovy.json.JsonSlurper
import io.vertx.core.json.JsonObject

import java.util.concurrent.atomic.AtomicBoolean

class UserParameters {

  private static String userParamsFile = ""

  private static JsonObject userJsonObject = null
  private static AtomicBoolean initialized = new AtomicBoolean(false)

  private static JsonObject readUserConf() {
    File f = new File(userParamsFile)
    if (f.exists()) {
      def slurper = new JsonSlurper()
      def jsonText = f.getText()
      return slurper.parseText(jsonText)
    }

    // no file exists return an empty json
    return new JsonObject()
  }

  private static void writeUserConf() {
    File f = new File(userParamsFile)
    f.write(userJsonObject.encodePrettily())
  }

  public static JsonObject loadUserParameters(String fileName) {
    if (initialized.compareAndSet(false, true)) {
      userParamsFile = fileName
      userJsonObject = readUserConf()
    }
    return userJsonObject
  }

  private static JsonObject getUserParameters() {
    return userJsonObject
  }

  public static setDevice(String address, String name) {
    JsonObject jsonObject = new JsonObject(['address': address, 'name': name])
    userJsonObject.put("device", jsonObject)
    writeUserConf()
  }

  public static JsonObject getDevice() {
    userJsonObject.getJsonObject("device")
  }
}
