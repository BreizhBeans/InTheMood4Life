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
package org.breizhbeans.itm4l.service

import com.google.common.base.Strings
import org.breizhbeans.itm4l.exception.JsonFormatException

class AbstractService {
  protected Map<String, Object> services = [:]

  protected String getStringAttribute(def request, String attrName, boolean mandatory) {
    String value = request[attrName]

    if (value==null && mandatory) {
      throw new JsonFormatException(JsonFormatException.JsonExceptionType.ATTRIBUTE_MISSING, attrName)
    }

    if (!(value instanceof String)) {
      println(value.getClass().name)
      throw new JsonFormatException(JsonFormatException.JsonExceptionType.BAD_ATTRIBUTE_TYPE, attrName, "string")
    }

    if (Strings.isNullOrEmpty(value)) {
      return null
    }

    return value
  }
}
