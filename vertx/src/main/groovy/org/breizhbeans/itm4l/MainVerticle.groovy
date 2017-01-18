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
package org.breizhbeans.itm4l

import io.vertx.core.logging.LoggerFactory
import io.vertx.groovy.core.http.HttpClient
import io.vertx.lang.groovy.GroovyVerticle
import org.breizhbeans.itm4l.parameters.UserParameters
import org.breizhbeans.itm4l.warp10.Warp10Client


class MainVerticle extends GroovyVerticle {

  def logger = LoggerFactory.getLogger(MainVerticle.class)

  @Override
  public void start() {
    logger.info "start MainVerticle"

    def config = context.config()
    String scriptsDir = config.get('scripts')
    String userParamFile = config.get('userParams')

    Warp10Client.open(config.get('warp10'))
    HttpClient client = vertx.createHttpClient()

    // load user configuration
    UserParameters.loadUserParameters(userParamFile)

    // deploy Web Server Verticle
    vertx.deployVerticle("groovy:org.breizhbeans.itm4l.verticle.impl.WebServer", ['config':config, 'instances':1]) { asyncResult ->
      if (asyncResult.succeeded()) {
        logger.info "WebServer Module deployed, deployment ID is ${asyncResult.result()}"
      } else {
        logger.error("WebServer Module deploy error - unable to start", asyncResult.cause())
      }
    }

    vertx.deployVerticle("groovy:org.breizhbeans.itm4l.verticle.impl.BluetoothVerticle", ['worker': true, 'config':config, 'instances':1]) { asyncResult ->
      if (asyncResult.succeeded()) {
        logger.info "WebServer Module deployed, deployment ID is ${asyncResult.result()}"
      } else {
        logger.error("WebServer Module deploy error - unable to start", asyncResult.cause())
      }
    }

    logger.info "MainVerticle started"

  }

  @Override
  public void stop() {
    logger.info "stop MainVerticle"
    // Kill the process
    vertx.close()

  }
}
