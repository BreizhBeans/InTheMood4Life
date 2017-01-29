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
package org.breizhbeans.itm4l.verticle.impl

import io.vertx.core.logging.LoggerFactory
import io.vertx.groovy.core.buffer.Buffer
import io.vertx.groovy.core.http.HttpClient
import io.vertx.lang.groovy.GroovyVerticle
import org.breizhbeans.itm4l.warp10.Warp10Client

class WarpAnalyser extends GroovyVerticle {
  def logger = LoggerFactory.getLogger(WarpAnalyser.class)

  HttpClient warp10Client

  long recorderTimerId = 0L

  @Override
  public void start() {
    warp10Client = vertx.createHttpClient(['keepAlive': true, 'maxPoolSize': 1])

    recorderTimerId = vertx.setPeriodic(1000, { id ->
      Warp10Client.exec(warp10Client, "NOW", { int statusCode, String statusMessage, Buffer buffer ->
        // logger.info("statusCode=${statusCode} message=${statusMessage} payload=${buffer.toString()}")

      })
    })
  }

  @Override
  public void stop() {
    logger.info "stop WarpAnalyser Verticle"
  }
}