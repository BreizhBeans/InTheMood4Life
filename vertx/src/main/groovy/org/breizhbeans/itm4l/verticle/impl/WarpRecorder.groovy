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
import io.vertx.groovy.core.eventbus.Message
import io.vertx.groovy.core.http.HttpClient
import io.vertx.lang.groovy.GroovyVerticle
import org.breizhbeans.itm4l.beddit.FrameDecoder
import org.breizhbeans.itm4l.warp10.Warp10Client

class WarpRecorder extends GroovyVerticle {
  def logger = LoggerFactory.getLogger(WarpRecorder.class)

  HttpClient warp10Client

  long recorderTimerId = 0L

  @Override
  public void start() {
    warp10Client = vertx.createHttpClient(['keepAlive': true, 'maxPoolSize': 1])

    vertx.eventBus().consumer("warpRecorder") { Message message ->
      // decode beddit dataframe and convert it into GTS
      byte[] payload = (byte[]) message.body()

      FrameDecoder.decode(payload)
    }

    // thread recorder
    recorderTimerId = vertx.setPeriodic(1000, { id ->
      try {
        // This handler will get called every second
        List<String> gts = FrameDecoder.getGtsBuffer()
        if (gts!=null && gts.size() > 0) {
          Warp10Client.update(warp10Client, gts)
          logger.debug("${gts.size()} gts recorded")
        } else {
          logger.debug("0 gts recorded")
        }
      } catch (Exception exp) {
        logger.error("Warp10 update error", exp)
      }
    })
  }

  @Override
  public void stop() {
    vertx.cancelTimer(recorderTimerId)
    logger.info "stop WarpRecorder Verticle"
  }
}
