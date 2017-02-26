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

import com.google.common.base.Strings
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.groovy.core.eventbus.EventBus
import io.vertx.groovy.core.http.HttpClient
import io.vertx.groovy.core.http.HttpServer
import io.vertx.groovy.core.http.HttpServerResponse
import io.vertx.groovy.ext.web.Router
import io.vertx.groovy.ext.web.RoutingContext
import io.vertx.groovy.ext.web.handler.CorsHandler
import io.vertx.groovy.ext.web.handler.StaticHandler
import io.vertx.lang.groovy.GroovyVerticle
import org.breizhbeans.itm4l.verticle.WorkerVerticleError
import org.breizhbeans.itm4l.warp10.Warp10Client

class WebServer extends GroovyVerticle {

  def logger = LoggerFactory.getLogger(WebServer.class)

  private HttpClient warp10Client = null
  private HttpServer httpServer = null

  @Override
  public void start() {
    def router = Router.router(vertx)

    def config = context.config()
    def webConfig = config.get("web")

    String pwaRoot = webConfig["path"]["pwa"]

    // local warp10 instance
    warp10Client = vertx.createHttpClient(['keepAlive': true, 'maxPoolSize': 4])

    // CORS Handler
    def corsHandler = CorsHandler.create("*")
        .allowedMethod(HttpMethod.GET)
        .allowedMethod(HttpMethod.POST)
        .allowedMethod(HttpMethod.OPTIONS)
        .allowedHeader("Content-Type")

    router.route().handler(corsHandler)

    router.get("/itm4l/*").handler(StaticHandler.create().setAllowRootFileSystemAccess(true).setWebRoot(pwaRoot).setCachingEnabled(false));

    router.route("/api/:module/:version/*").handler(this.&deviceApi)
    router.post("/warp10/api/v0/exec").handler(this.&warp10ExecPump)
    router.get("/warp10/quantum/*").handler(this.&quantumPump)

    httpServer = vertx.createHttpServer()
    // listen on any interface
    httpServer.requestHandler(router.&accept).listen((int) webConfig["port"], (String) webConfig["host"])
  }


  @Override
  public void stop() {
    logger.info "stop WebServer Verticle"
    if (httpServer) {
      httpServer.close()
    }
  }

  private void warp10ExecPump(RoutingContext ctx) {
    Warp10Client.pumpExec(warp10Client, ctx)
  }

  private void quantumPump(RoutingContext ctx) {
    Warp10Client.pumpQuantum(warp10Client, ctx)
  }

  private void deviceApi(RoutingContext ctx) {
    EventBus eb = vertx.eventBus()
    String module = ctx.request().getParam("module")
    String version = ctx.request().getParam("version")
    String service = null
    String[] pathSplit = ctx.request().path().split("/$module/$version/")

    if (pathSplit.size() == 2) {
      service = pathSplit[1]
    }

    if (Strings.isNullOrEmpty(module) || Strings.isNullOrEmpty(version) || Strings.isNullOrEmpty(service)) {
      sendJson(ctx.response(), 404, WorkerVerticleError.NOT_FOUND, "API Service not found", "module=${module} version=${version} service=${service}")
      ctx.response().end()
      return
    }

    ctx.request().bodyHandler { buffer ->
      def options = [
        headers: [
          "SERVICE": service,
          "METHOD": ctx.request().method().name()
        ]
      ]

      String address = "${module}-${version}"

      eb.send(address, buffer, options) { message ->
        ctx.response().chunked = true

        // event bus failure
        if (message.failed()) {
          sendJson(ctx.response(), 500, WorkerVerticleError.SERVER_ERROR, "Event bus error", "unexpected error T${System.currentTimeMillis()}")
          ctx.response().end()
          return
        }

        // the message is a JSON message
        if (message.result().body() instanceof Map<String,Object>) {
          Map<String,Object> response = (Map<String,Object>) message.result().body()

          // the response is a json (normal response or JSON)
          JsonObject outputJson = new JsonObject(response)

          if (response.containsKey('error')) {
            switch (WorkerVerticleError.valueOf(response.get('type'))) {

              case WorkerVerticleError.NOT_FOUND:
                sendJson(ctx.response(), 404, outputJson.encode())
                break;
              case WorkerVerticleError.BAD_REQUEST:
                sendJson(ctx.response(), 400, outputJson.encode())
                break;

              case WorkerVerticleError.SERVER_ERROR:
                sendJson(ctx.response(), 500, outputJson.encode())
                break;

              // unknown errors = 500
              default:
                sendJson(ctx.response(), 500, outputJson.encode())
            }
          } else {
            // Not an error Json response from the module
            sendJson(ctx.response(), 200, outputJson.encode())
          }
        }
        ctx.response().end()
      }
    }
  }

  private void sendJson(HttpServerResponse response, int status, WorkerVerticleError errorType, String errorName, String message) {
    JsonObject error = new JsonObject()
    error.put("error", true)
    error.put("type", errorType.name())
    error.put("name", errorName)
    if (!Strings.isNullOrEmpty(message)) {
      error.put("message", message)
    }
    sendJson(response, status, error.encode())
  }

  private void sendJson(HttpServerResponse response, int status, String jsonResponse) {
    response.setStatusCode(status)
    response.headers().set("Content-Type", "application/json")
    response.write(jsonResponse)
  }
}