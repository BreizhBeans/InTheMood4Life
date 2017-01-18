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
package org.breizhbeans.itm4l.warp10

import groovy.json.JsonSlurper
import io.vertx.core.logging.Logger
import io.vertx.core.logging.LoggerFactory
import io.vertx.groovy.core.http.HttpClient
import io.vertx.groovy.core.streams.Pump
import io.vertx.groovy.ext.web.RoutingContext

import java.util.concurrent.atomic.AtomicBoolean

class Warp10Client {
  private static Logger logger = LoggerFactory.getLogger(Warp10Client.class)

  private static AtomicBoolean opened = new AtomicBoolean(false)
  private static String host
  private static String writeToken
  private static String readToken
  private static String tokenFile
  private static int warp10Port
  private static int quantumPort
  private static String execEndpoint
  private static String updateEndpoint


  public static void open(Map<String,Object> warp10Config) {
    if (opened.compareAndSet(false, true)) {
      host = warp10Config["host"]
      warp10Port = (int) warp10Config["warp10Port"]
      quantumPort = (int) warp10Config["quantumPort"]
      execEndpoint = warp10Config["exec"]
      updateEndpoint = warp10Config["update"]
      tokenFile = warp10Config["tokens"]
    }
  }

  public static getWriteToken() {
    if (writeToken != null) {
      return writeToken
    }
    File f = new File(tokenFile)
    def slurper = new JsonSlurper()
    def jsonText = f.getText()
    def json = slurper.parseText( jsonText )

    writeToken = json.write.token

    return writeToken
  }

  public static getReadToken() {
    if (readToken != null) {
      return readToken
    }
    File f = new File(tokenFile)
    def slurper = new JsonSlurper()
    def jsonText = f.getText()
    def json = slurper.parseText( jsonText )

    readToken = json.read.token

    return readToken
  }

  public static void pumpExec(HttpClient client, RoutingContext ctx)  {
    def warpRequest = client.post(warp10Port,host, execEndpoint) { warpResponse ->
      // set response headers
      ctx.response().chunked = true
      ctx.response().statusCode = warpResponse.statusCode()

      // Pump the response to the input request output stream
      def pump = Pump.pump(warpResponse, ctx.response())
      warpResponse.endHandler {
        ctx.response().end()
      }
      pump.start()
    }

    warpRequest.chunked = true

    // writes the read token
    warpRequest.write("'${getReadToken()}' 'readToken' STORE\n")

    // writes the warp script
    ctx.request().handler { requestBuffer ->
      warpRequest.write(requestBuffer)
    }

    //ends the request
    ctx.request().endHandler {
      warpRequest.end()
    }
  }

  public static void pumpQuantum(HttpClient client, RoutingContext ctx)  {
    def quantumRequest = client.post(quantumPort,host, ctx.request().path().substring(15)) { warpResponse ->
      // set response headers
      ctx.response().chunked = true
      ctx.response().statusCode = warpResponse.statusCode()

      // Pump the response to the input request output stream
      def pump = Pump.pump(warpResponse, ctx.response())
      warpResponse.endHandler {
        ctx.response().end()
      }
      pump.start()
    }

    quantumRequest.chunked = true

    // writes the warp script
    ctx.request().handler { requestBuffer ->
      quantumRequest.write(requestBuffer)
    }

    //ends the request
    ctx.request().endHandler {
      quantumRequest.end()
    }
  }
}

