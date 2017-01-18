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
import org.breizhbeans.itm4l.service.impl.BluetoothService
import org.breizhbeans.itm4l.verticle.AbstractWorkerVerticle


class BluetoothVerticle extends AbstractWorkerVerticle {

  def logger = LoggerFactory.getLogger(BluetoothVerticle.class)

  @Override
  public void start() {
    EventBus eb = vertx.eventBus()

    BluetoothService bluetoothService = new BluetoothService(context.config())
    registerService(logger, eb, "ble-v1", bluetoothService)
    logger.info("Bluetooth verticle started")
  }

  @Override
  public void stop() {

  }
}
