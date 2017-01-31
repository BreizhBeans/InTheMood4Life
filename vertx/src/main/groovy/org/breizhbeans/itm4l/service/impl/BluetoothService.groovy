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
package org.breizhbeans.itm4l.service.impl

import com.google.common.base.Strings
import com.google.common.io.BaseEncoding
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.groovy.core.Vertx
import io.vertx.groovy.core.buffer.Buffer
import org.breizhbeans.itm4l.beddit.FrameDecoder
import org.breizhbeans.itm4l.exception.FunctionalException
import org.breizhbeans.itm4l.exception.Functionals
import org.breizhbeans.itm4l.parameters.UserParameters
import org.breizhbeans.itm4l.service.AbstractService
import org.breizhbeans.itm4l.service.ServiceRequest
import com.julienviet.groovy.childprocess.Process

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Matcher
import java.util.regex.Pattern

class BluetoothService extends AbstractService {

  public static enum BleState {
    SCAN, RECORDING, IDLE;
  }


  private static enum BleConnectionState {
    DISCONNECTED, CONNECTED;
  }


  private static BleState bleState = BleState.IDLE
  private static BleConnectionState bleConnectionState = BleConnectionState.DISCONNECTED
  private static AtomicInteger bleMessagesReceived = new AtomicInteger(0)

  private static Process process = null
  private String scriptsDir = null
  private Map<String,String> devices = [:]
  private int messageRate = 0
  private int deviceMuteSince = 0

  def logger = LoggerFactory.getLogger(BluetoothService.class)

  public BluetoothService(Map<String, Object> config) {
    services.put('scan/start', this.&startScan)
    services.put('scan/devices', this.&devices)
    services.put('scan/stop', this.&stopScan)

    services.put('pair', this.&pair)

    services.put('record/start', this.&startRecord)
    services.put('record/stop', this.&stopRecord)
    services.put('status', this.&status)

    scriptsDir= config.get("scripts")
  }

  //
  // Monitorring methods
  //

  public void monitoring(Vertx vertx) {
    messageRate = bleMessagesReceived.getAndSet(0)

    if (!bleState.equals(BleState.RECORDING)) {
      // nothing to do else
      return
    }

    if (messageRate == 0) {
      deviceMuteSince++
    } else {
      deviceMuteSince = 0
    }

    if (deviceMuteSince > 10) {
      restartGatttool(vertx)
    }
  }


  //
  // API methods
  //
  private void status(ServiceRequest context, def request) {
    JsonObject output = new JsonObject()
    output.put('status', bleState.name())


    JsonObject beddit = new JsonObject()
    beddit.put('status', bleConnectionState.name())
    beddit.put('messageRate', messageRate)

    output.put('beddit', beddit)

    context.replyHandler.call(output)
  }

  private void startScan(ServiceRequest context, def request) {
    if (!bleState.equals(BleState.IDLE)) {
      throw new FunctionalException(context.service, Functionals.BLE_NOT_IDLE, "ble is not IDLE (${bleState.name()})")
    }

    // kill current process is exists
    if (process!=null && process.running) {
      logger.debug("bt:lescan:kill:pid:${process.pid()}")
      process.kill(true)
    }

    devices.clear()

    def options = [
        env:Process.env()
    ]
    def resetBtProcess = Process.create(context.vertx, "${scriptsDir}/resetbt.sh", options)

    resetBtProcess.exitHandler({ code ->
      logger.debug("bt:lescan:reset:status:${code}")

      process = Process.create(context.vertx, "stdbuf", [ "-i0", "-o0", "-e0", "hcitool","lescan"], options)


      process.stdout().handler({ buff ->
        // Extract bluetooth address
        // ^([0-9A-F]{2}:[0-9A-F]{2}:[0-9A-F]{2}:[0-9A-F]{2}:[0-9A-F]{2}:[0-9A-F]{2}) (Beddit.*?$)
        Pattern btPattern = Pattern.compile("^([0-9A-F]{2}:[0-9A-F]{2}:[0-9A-F]{2}:[0-9A-F]{2}:[0-9A-F]{2}:[0-9A-F]{2}) (Beddit.*?\$)");
        String btScan = buff.toString()
        Matcher m = btPattern.matcher(btScan)

        if (m.find()) {
          logger.debug("bt:lescan:match:${m.group(1)}:${m.group(2)}")
          devices.put(m.group(1), m.group(2))
        } else {
          logger.debug("bt:lescan:nomatch:${btScan}")
        }
      })

      process.stderr().handler({ buff ->
        logger.debug("bt:lescan:err:${buff.toString()}")
      })

      process.start({ processStarted ->
        process = processStarted
        logger.debug("bt:lescan:pid:${processStarted.pid()}")
      })
    })


    resetBtProcess.start()

    // update the ble scan
    bleState= BleState.SCAN
    context.replyHandler.call(new JsonObject())
  }

  private void stopScan(ServiceRequest context, def request) {
    if (!bleState.equals(BleState.SCAN)) {
      throw new FunctionalException(context.service, Functionals.BLE_SCAN_DISABLED, "Scan not running state=(${bleState.name()})")
    }

    stopScan()
    context.replyHandler.call(new JsonObject())
  }

  private void devices(ServiceRequest context, def request) {
    def output = new JsonObject()

    JsonArray jsonDevices = new JsonArray()

    for ( d in devices ) {
      JsonObject jsonDevice = new JsonObject()
      jsonDevice.put("address", d.key)
      jsonDevice.put("name", d.value)
      jsonDevices.add(jsonDevice)
    }

    output.put("devices", jsonDevices)
    context.replyHandler.call(output)
  }


  private void pair(ServiceRequest context, def request) {
    // Paring only available during the scan process
    if (!bleState.equals(BleState.SCAN)) {
      throw new FunctionalException(context.service, Functionals.BLE_SCAN_DISABLED, "Scan not running state=(${bleState.name()})")
    }
    // get the address
    String address = getStringAttribute(request, "address", true)

    String deviceName = devices.get(address)

    if (Strings.isNullOrEmpty(deviceName)) {
      throw new FunctionalException(context.service, Functionals.BLE_UNKNOWN_ADDRESS, "Unknown bluetooth address")
    }

    // save the address
    UserParameters.setDevice(address, deviceName)

    // stop the scan
    stopScan()
    def output = new JsonObject()
    context.replyHandler.call(output)
  }


  private void broadcastBleState(Vertx vertx) {
    JsonObject jsonMessage = new JsonObject()
    jsonMessage.put("state", bleState.name())
    vertx.eventBus().send("warpAnalyser", jsonMessage)
  }


  //
  // START Record
  //
  private void startRecord(ServiceRequest context, def request) {
    if (!bleState.equals(BleState.IDLE)) {
      throw new FunctionalException(context.service, Functionals.BLE_NOT_IDLE, "Ble is not IDLE state=(${bleState.name()})")
    }

    JsonObject device = UserParameters.getDevice()

    if (device == null) {
      throw new FunctionalException(context.service, Functionals.BLE_NO_DEVICE, "No Beddit device paired")
    }

    String address = device.getString("address")

    // kill current process is exists
    if (process!=null && process.running) {
      logger.debug("bt:record:kill:pid:${process.pid()}")
      process.kill(true)
      throw new FunctionalException(context.service, Functionals.BLE_PROCESS_RUNNING, "BLE process still running")
    }

    logger.info("bt:start:gatttool")
    spawnGatttool(context.vertx, address)
    bleState= BleState.RECORDING

    // broadcast the state
    broadcastBleState(context.vertx)

    def output = new JsonObject()
    context.replyHandler.call(output)
  }

  private void restartGatttool(Vertx vertx) {
    if (!bleState.equals(BleState.RECORDING)) {
      return
    }

    logger.info("bt:restart:gatttool")

    // stop gatttool
    stopGatttool(true)

    JsonObject device = UserParameters.getDevice()

    if (device == null) {
      logger.info("bt:restart:no device")
      return
    }

    // Spawn if gatttool is stopped
    if (process==null) {
      String address = device.getString("address")
      logger.info("bt:restart:spawn gatttool address${address}")
      spawnGatttool(vertx, address)
    }
  }

  private void stopRecord(ServiceRequest context, def request) {
    if (!bleState.equals(BleState.RECORDING)) {
      throw new FunctionalException(context.service, Functionals.BLE_NOT_RECORDING, "Ble is not recording state=(${bleState.name()})")
    }

    logger.info("bt:stop:gatttool")
    stopGatttool(false)

    bleState= BleState.IDLE

    // broadcast the state
    broadcastBleState(context.vertx)

    def output = new JsonObject()
    context.replyHandler.call(output)
  }


  private void spawnGatttool(Vertx vertx, String address) {
    def options = [
        env:Process.env()
    ]

    process = Process.create(vertx, "gatttool", ["-b", address, "-I"], options)

    process.stdout().handler({ buff ->
      String message = buff.toString()
      bleMessagesReceived.andIncrement

      switch (bleConnectionState) {
        case BleConnectionState.DISCONNECTED:
          // dirty process scrapping
          if (message.contains("Connection successful")) {
            // writes 01 on the handle 0x0010
            logger.info("ble:spawnGatttool:connected:to ${address}")
            bleConnectionState = BleConnectionState.CONNECTED
            FrameDecoder.initDecoder()
            process.stdin().write(Buffer.buffer("char-write-cmd 0x0010 0100\n"))
            process.stdin().write(Buffer.buffer("char-write-cmd 0x000e 01\n"))
          }

          if (message.contains("connect error")) {
            logger.info("ble:spawnGatttool:connect error: ${message}")
            bleConnectionState = BleConnectionState.DISCONNECTED
            process.kill(true)
          }
          break;

        case BleConnectionState.CONNECTED:
          // this is a handle notification
          if (message.contains("0x000e")) {
            //println("recording:${message}")
            // take first the timestamp before any processing
            String value = null
            int startIndex, endIndex
            try {
              long timestamp = System.currentTimeMillis() * 1000L
              // keep only data after value
              startIndex = message.lastIndexOf("value:")
              // cut after the first \n
              endIndex = message.indexOf("\n", startIndex)

              if ((startIndex == -1) || (endIndex == -1)) {
                value = null
              } else {
                value = message.substring(startIndex + 7, endIndex)
              }

              if (!Strings.isNullOrEmpty(value)) {
                // removes spaces
                value = value.replace(" ", "")
                byte[] payload = BaseEncoding.base16().decode(value.toUpperCase())

                //extract datagram sequence number for debug
                logger.debug("recording: datagram number=${payload[1] & 255}")

                // post this data on the event bus
                ByteBuffer outputBuffer = ByteBuffer.allocate(Long.BYTES + payload.length);
                outputBuffer.putLong(timestamp)
                outputBuffer.put(payload)
                //logger.info("recieved ${value}")
                vertx.eventBus().send("warpRecorder", outputBuffer.array())
              } else {
                logger.error("recording: unknown message=/${message}/")
              }
            } catch(Exception exp) {
              logger.error("recording: /${value}/${startIndex}/${endIndex}/", exp)
            }
          }
          break
      }
    })

    process.exitHandler( { exitStatus ->
      logger.info("gatttool exit status=${exitStatus}")
      bleConnectionState = BleConnectionState.DISCONNECTED
      process = null
    })

    process.start()

    // try to connect to the Beddit device
    process.stdin().write(Buffer.buffer("connect\n"))
    deviceMuteSince = 0
  }

  private void stopGatttool(boolean force) {
    if (process!=null && process.running) {
      logger.info("stop gatttool process pid=${process.pid()}")
      // sends stop command
      process.stdin().write(Buffer.buffer("char-write-cmd 0x0010 0000\n"))
      process.stdin().write(Buffer.buffer("disconnect\n"))
      process.stdin().write(Buffer.buffer("quit\n"))
      process.kill(force)
    }
  }

  public void stop() {
    // kill current process is exists
    if (process!=null && process.running) {
      process.kill(true)
      process = null
    }

    // update the ble scan
    bleState= BleState.IDLE

  }

  private void stopScan() {
    // kill current process is exists
    stop()
    devices.clear()
  }
}
