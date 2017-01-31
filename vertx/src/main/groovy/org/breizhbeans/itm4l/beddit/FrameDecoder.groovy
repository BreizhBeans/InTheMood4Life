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

  // 140 HZ
  private static final long sensorPeriod = 7142

  private static def logger = LoggerFactory.getLogger(FrameDecoder.class)

  static void initDecoder() {
    initDecoder(-1)
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

  static synchronized int decode(byte[] payload) {
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
      int startRecordingTimeStamp = -1

      // defensible control
      if (packetType != 128) {
        throw new Exception("invalid packet type")
      }

      // timestamp of the first datagram
      if (currentTimeStamp == -1) {
        currentTimeStamp = timestamp
        startRecordingTimeStamp = timestamp
        addGts("${startRecordingTimeStamp}// recording{} 0")
      }

      if (sequenceNumber != nextSequenceNumber) {
        // get the drift
        int  drift = sequenceNumber - nextSequenceNumber
        // positive drift, the next message is in the same sequence
        // negative drift, the next message is in the next sequence (start 0)
        if (drift == 0) {
          // complete loop lost
          throw new Exception("sequence lost")
        } else if (drift < 0) {
          drift = 255 + drift + 1
        }

        addGts("${timestamp}// bcg.lost{} ${nextSequenceNumber}")

        // reajust the nextSequenceNumber
        nextSequenceNumber = sequenceNumber

        // shift the current timestamp
        currentTimeStamp += (sensorPeriod * drift * 9)
        logger.debug("messages lost=${drift} seq=${sequenceNumber}")


      } else {
        // add drift (debug)
        addGts("${timestamp}// bcg.drift{} ${timestamp-currentTimeStamp}")
      }

      while(buffer.position() < payload.length) {
        long sensorValue = buffer.get() & 0xFF
        sensorValue += (buffer.get() & 0xFF) << 8

        addGts("${currentTimeStamp}// bcg.raw{} $sensorValue")
        currentTimeStamp+=sensorPeriod
      }

      // next sequence number modulo 255
      ++nextSequenceNumber
      nextSequenceNumber &= 0x000000FF

      return startRecordingTimeStamp
    } catch (Exception exp) {
      logger.error(exp.message)
    }
  }
}
