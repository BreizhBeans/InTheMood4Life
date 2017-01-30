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

import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.groovy.core.buffer.Buffer
import io.vertx.groovy.core.eventbus.Message
import io.vertx.groovy.core.http.HttpClient
import io.vertx.lang.groovy.GroovyVerticle
import org.breizhbeans.itm4l.service.impl.BluetoothService
import org.breizhbeans.itm4l.warp10.Warp10Client

class WarpAnalyser extends GroovyVerticle {
  def logger = LoggerFactory.getLogger(WarpAnalyser.class)

  HttpClient warp10Client

  Long analyserTimerId = null

  @Override
  public void start() {
    warp10Client = vertx.createHttpClient(['keepAlive': true, 'maxPoolSize': 1])


    vertx.eventBus().consumer("warpAnalyser") { Message message ->
      JsonObject jsonObject = (JsonObject) message.body()


      def currentState = BluetoothService.BleState.valueOf(jsonObject.getString("state"))

      if (analyserTimerId!=null) {
        vertx.cancelTimer(analyserTimerId)
        analyserTimerId = null
      }

      switch (currentState) {
        case BluetoothService.BleState.RECORDING:
          logger.info("start analyser")
          analyserTimerId = vertx.setPeriodic(60000, { id ->
            analyserTimerId = id
            // Build script
            String script = "'${Warp10Client.getReadToken()}' 'token' STORE\n '${Warp10Client.getWriteToken()}' 'writeToken' STORE\n 7142 'sensorPeriod' STORE\n @streamProcessing/analyse"
            Warp10Client.exec(warp10Client, script, { int statusCode, String statusMessage, Buffer buffer ->
              logger.info("statusCode=${statusCode} message=${statusMessage} payload=${buffer.toString()}")
            })
          })
          break;
      }
    }
  }

  @Override
  public void stop() {
    logger.info "stop WarpAnalyser Verticle"
    vertx.cancelTimer(analyserTimerId)
  }
}