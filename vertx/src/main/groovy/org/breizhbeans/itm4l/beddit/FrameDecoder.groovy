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
package org.breizhbeans.itm4l.beddit

import io.vertx.core.logging.LoggerFactory

import java.nio.ByteBuffer
import java.util.concurrent.Semaphore

class FrameDecoder {

  private static int nextSequenceNumber = 0
  private static long currentTimeStamp = 0L
  private static List<String> gtsBuffer = null
  private static Semaphore mutex = new Semaphore(1)

  private static def logger = LoggerFactory.getLogger(FrameDecoder.class)

  static void initDecoder() {
    initDecoder(System.currentTimeMillis() * 1000)
  }

  static void initDecoder(long timeStamp) {
    nextSequenceNumber = 0
    currentTimeStamp = timeStamp
    gtsBuffer = []
  }

  static List<String> getGtsBuffer() {
    List<String> output = null
    try {
      mutex.acquire()
      if (gtsBuffer != null && gtsBuffer.size() > 0) {
        output = gtsBuffer
        gtsBuffer = []
      }
    } catch (Exception exp) {
      exp.printStackTrace()
    } finally {
      mutex.release()
    }
    return output
  }

  private static void addGts(String gts) {
    try {
      mutex.acquire()
      if (gtsBuffer != null) {
        gtsBuffer.add(gts)
      }
    } catch (Exception exp) {
      exp.printStackTrace()
    } finally {
      mutex.release()
    }
  }

  static synchronized void decode(byte[] payload) {
    try {
      // defensible control
      if (payload.length < 8 || payload.length % 2 != 0) {
        throw new Exception("invalid datagram (length)")
      }

      ByteBuffer buffer = ByteBuffer.allocate(payload.length);
      buffer.put(payload, 0, payload.length);
      buffer.flip();

      long timestamp =  buffer.getLong();
      int packetType =  buffer.get() & 255
      int sequenceNumber = buffer.get() & 255

      // defensible control
      if (packetType != 128) {
        throw new Exception("invalid packet type")
      }

      // TODO improve robustest for datagram
      if (sequenceNumber != nextSequenceNumber) {
        // get the drift
        int  drift = sequenceNumber - nextSequenceNumber
        if (drift > 0) {
          currentTimeStamp += 7000 * drift * 9
          nextSequenceNumber += drift
          logger.error("messages lost=${drift} seq=${sequenceNumber} nextseq=${nextSequenceNumber}")

        } else {
          throw new Exception("sequence lost")
        }
      }

      long firstDataPointTimeStamp  = currentTimeStamp;

      while(buffer.position() < payload.length) {
        long sensorValue = buffer.get() & 0xFF
        sensorValue += (buffer.get() & 0xFF) << 8

        addGts("${currentTimeStamp}// bcg.raw{} $sensorValue")
        currentTimeStamp+=7000
      }

      // next sequence number modulo 255
      ++nextSequenceNumber
      nextSequenceNumber &= 255;

      // add drift (debug)
      addGts("${timestamp}// bcg.drift{} ${timestamp-firstDataPointTimeStamp}")
    } catch (Exception exp) {
      logger.error(exp.message)
    }
  }
}
