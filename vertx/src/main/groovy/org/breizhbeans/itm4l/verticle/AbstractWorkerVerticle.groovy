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
package org.breizhbeans.itm4l.verticle

import com.google.common.base.Strings
import groovy.json.JsonException
import groovy.json.JsonSlurper
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.Logger
import io.vertx.groovy.core.buffer.Buffer
import io.vertx.groovy.core.eventbus.EventBus
import io.vertx.groovy.core.eventbus.Message
import io.vertx.lang.groovy.GroovyVerticle
import org.breizhbeans.itm4l.exception.FunctionalException
import org.breizhbeans.itm4l.exception.Functionals
import org.breizhbeans.itm4l.exception.JsonFormatException
import org.breizhbeans.itm4l.exception.NotFoundException
import org.breizhbeans.itm4l.service.AbstractService
import org.breizhbeans.itm4l.service.ServiceRequest


class AbstractWorkerVerticle extends GroovyVerticle {

  protected registerService(Logger logger, EventBus eb, String address, AbstractService apiService) {
    eb.consumer(address) { Message message ->
      try {
        // Extract the JSON request (can be null)
        String data = "{}"

        if( message.body() != null) {
          Buffer buffer = (Buffer) message.body()
          if (buffer.length() > 0)
            data = buffer.toString()
        }

        def slurper = new JsonSlurper()
        def request = slurper.parseText(data)


        ServiceRequest serviceRunner = new ServiceRequest(vertx)

        serviceRunner.replyHandler { JsonObject replyMessage ->
          message.reply(replyMessage)
        }.errorHandler { Exception exp ->
          throw exp
        }.callService(apiService, request, message.headers())

      } catch (NotFoundException notFoundException) {
        message.reply(getError(WorkerVerticleError.NOT_FOUND, Functionals.UNKNOWN_SERVICE.name(), "${notFoundException.serviceName} does not exists"))
      } catch (JsonFormatException jsonFmtExp) {
        message.reply(getError(WorkerVerticleError.BAD_REQUEST, jsonFmtExp))
      } catch (JsonException jsonExp) {
        message.reply(getError(WorkerVerticleError.BAD_REQUEST, Functionals.PARSING.name(), jsonExp.message))
      } catch (FunctionalException serviceExp) {
        message.reply(getError(WorkerVerticleError.BAD_REQUEST, serviceExp.ferror.name(), serviceExp.message))
      } catch (Exception exp) {
        logger.error("unexpected error", exp)
        message.reply(getError(WorkerVerticleError.SERVER_ERROR, "unexpected", "something goes wrong t=${System.currentTimeMillis()}"))
      }
    }
  }

  protected JsonObject getError(WorkerVerticleError errorType, JsonFormatException exp ) {
    String message
    switch (exp.jsonExceptionType) {
      case JsonFormatException.JsonExceptionType.ATTRIBUTE_MISSING:
        message = "attribute ${exp.attributeName} is missing"
        break
      case JsonFormatException.JsonExceptionType.BAD_ATTRIBUTE_TYPE:
        message = "bad attribute type ${exp.attributeName}"
        break
      default:
        message = "default error attr=${exp.attributeName}"
    }

    return getError(errorType, exp.jsonExceptionType.name(), message)
  }

  protected JsonObject getError(WorkerVerticleError errorType, String errorName, String message) {
    JsonObject error = new JsonObject()
    error.put("error", true)
    error.put("type", errorType.name())
    error.put("name", errorName)
    if (!Strings.isNullOrEmpty(message)) {
      error.put("message", message)
    }
    return error
  }
}
