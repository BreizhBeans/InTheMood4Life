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
import io.vertx.groovy.core.eventbus.EventBus
import org.breizhbeans.itm4l.beddit.FrameDecoder
import org.breizhbeans.itm4l.service.impl.BluetoothService
import org.breizhbeans.itm4l.verticle.AbstractWorkerVerticle
import org.breizhbeans.itm4l.warp10.Warp10Client

class BluetoothVerticle extends AbstractWorkerVerticle {

  BluetoothService bluetoothService = null
  long timerId = 0L

  def logger = LoggerFactory.getLogger(BluetoothVerticle.class)

  @Override
  public void start() {
    EventBus eb = vertx.eventBus()

    bluetoothService = new BluetoothService(context.config())
    registerService(logger, eb, "ble-v1", bluetoothService)
    logger.info("Bluetooth verticle started")

    // BLE monitoring
    timerId = vertx.setPeriodic(1000, { id ->
      try {
        bluetoothService.monitoring(vertx)
      } catch(Exception exp) {
        logger.error("ble:monitoring error", exp)
      }
    })
  }

  @Override
  public void stop() {
    if (bluetoothService != null) {
      bluetoothService.stop()
    }
    vertx.cancelTimer(timerId)
    logger.info("Bluetooth verticle stopped")
  }
}