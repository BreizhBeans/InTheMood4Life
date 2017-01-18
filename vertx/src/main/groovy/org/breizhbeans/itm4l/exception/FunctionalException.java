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
package org.breizhbeans.itm4l.exception;

public class FunctionalException extends Exception {

  private String serviceName;

  private Functionals ferror;

  public FunctionalException(String serviceName, Functionals ferror, String message) {
    super(message);
    this.serviceName = serviceName;
    this.ferror = ferror;
  }

  public String getServiceName() {
    return serviceName;
  }

  public Functionals getFerror() {
    return  ferror;
  }

}
