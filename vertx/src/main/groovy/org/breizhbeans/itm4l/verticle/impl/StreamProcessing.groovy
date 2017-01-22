package org.breizhbeans.itm4l.verticle.impl

import io.vertx.core.logging.LoggerFactory
import io.vertx.groovy.core.eventbus.Message
import io.vertx.groovy.core.http.HttpClient
import io.vertx.lang.groovy.GroovyVerticle
import org.breizhbeans.itm4l.beddit.FrameDecoder
import org.breizhbeans.itm4l.warp10.Warp10Client

import java.util.concurrent.atomic.AtomicLong

class StreamProcessing extends GroovyVerticle {
  def logger = LoggerFactory.getLogger(StreamProcessing.class)

  HttpClient warp10Client

  AtomicLong messageRecieved = new AtomicLong(0)
  @Override
  public void start() {
    warp10Client = vertx.createHttpClient(['keepAlive': true, 'maxPoolSize': 1])

    vertx.eventBus().consumer("streamProcessing") { Message message ->
      // decode beddit dataframe and convert it into GTS
      byte[] payload = (byte[]) message.body()

      FrameDecoder.decode(payload)
      messageRecieved.incrementAndGet()
    }


    vertx.setPeriodic(1000, { id ->
      logger.info("${messageRecieved.getAndSet(0)} messages recieved")
    })

    // thread recorder
    vertx.setPeriodic(1000, { id ->
      try {
        // This handler will get called every second
        List<String> gts = FrameDecoder.getGtsBuffer()
        if (gts!=null && gts.size() > 0) {
          Warp10Client.update(warp10Client, gts)
          logger.info("${gts.size()} gts recorded")
        } else {
          logger.info("0 gts recorded")
        }
      } catch (Exception exp) {
        exp.printStackTrace()
      }
    })

  }

  @Override
  public void stop() {

  }

}
