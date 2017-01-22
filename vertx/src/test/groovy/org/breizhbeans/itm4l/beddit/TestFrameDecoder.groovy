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

import org.junit.Test

import java.nio.ByteBuffer

class TestFrameDecoder extends GroovyTestCase {

  @Test
  public void testDecodeBedditFrame() {
    String frame = "8000d36bab672865256485646f65a2668b671768"
    byte[] payload = frame.decodeHex()

    ByteBuffer outputBuffer = ByteBuffer.allocate(Long.BYTES + payload.length);
    outputBuffer.putLong(0L)
    outputBuffer.put(payload)


    FrameDecoder.initDecoder(0L)
    FrameDecoder.decode(outputBuffer.array())

    List<String>  outputGts = FrameDecoder.getGtsBuffer()
    List<String> expectedGts = ['0// bcg.raw{} 27603', '7000// bcg.raw{} 26539', '14000// bcg.raw{} 25896', '21000// bcg.raw{} 25637', '28000// bcg.raw{} 25733', '35000// bcg.raw{} 25967', '42000// bcg.raw{} 26274', '49000// bcg.raw{} 26507', '56000// bcg.raw{} 26647', '0// bcg.drift{} 0']

    assert outputGts == expectedGts
  }
}
