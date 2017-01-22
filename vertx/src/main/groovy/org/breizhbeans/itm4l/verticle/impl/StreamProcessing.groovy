package org.breizhbeans.itm4l.verticle.impl

import io.vertx.core.logging.LoggerFactory
import io.vertx.groovy.core.eventbus.Message
import io.vertx.groovy.core.http.HttpClient
import io.vertx.lang.groovy.GroovyVerticle
import org.breizhbeans.itm4l.beddit.FrameDecoder
import org.breizhbeans.itm4l.warp10.Warp10Client

class StreamProcessing extends GroovyVerticle {
  def logger = LoggerFactory.getLogger(StreamProcessing.class)

  HttpClient warp10Client

  long timerId = 0L

  @Override
  public void start() {
    warp10Client = vertx.createHttpClient(['keepAlive': true, 'maxPoolSize': 1])

    vertx.eventBus().consumer("streamProcessing") { Message message ->
      // decode beddit dataframe and convert it into GTS
      byte[] payload = (byte[]) message.body()

      FrameDecoder.decode(payload)
    }

    // thread recorder
    timerId = vertx.setPeriodic(1000, { id ->
      try {
        // This handler will get called every second
        List<String> gts = FrameDecoder.getGtsBuffer()
        if (gts!=null && gts.size() > 0) {
          Warp10Client.update(warp10Client, gts)
          logger.debug("${gts.size()} gts recorded")
        } else {
          logger.debug("0 gts recorded")
        }
      } catch (Exception exp) {
        logger.error("Warp10 update error", exp)
      }
    })
  }

  @Override
  public void stop() {
    vertx.cancelTimer(timerId)
  }
}
