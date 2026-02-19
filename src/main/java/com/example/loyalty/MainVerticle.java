package com.example.loyalty;

import com.example.loyalty.config.ServiceConfig;
import com.example.loyalty.handler.LoggingHandler;
import com.example.loyalty.handler.PointsHandler;
import com.example.loyalty.service.PointsService;
import com.example.loyalty.service.impl.HttpFxClient;
import com.example.loyalty.service.impl.HttpPromoClient;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainVerticle extends AbstractVerticle {
  private static final Logger log = LoggerFactory.getLogger(MainVerticle.class);

  @Override
  public void start(Promise<Void> startPromise) {
    ServiceConfig config = ServiceConfig.fromSystemProperties();
    log.info("Starting with config: fxHost={}, fxPort={}, promoHost={}, promoPort={}, httpPort={}",
        config.fxHost(), config.fxPort(), config.promoHost(), config.promoPort(), config.httpPort());

    var fxClient = new HttpFxClient(vertx, config);
    var promoClient = new HttpPromoClient(vertx, config);
    var service = new PointsService(fxClient, promoClient, config.pointsCap());
    var handler = new PointsHandler(service);
    var loggingHandler = new LoggingHandler();

    Router router = Router.router(vertx);
    router.route().handler(loggingHandler::handle);
    router.route().handler(BodyHandler.create());

    router.post("/v1/points/quote").handler(handler::handle);
    router.get("/health/live").handler(ctx -> ctx.response().end("OK"));
    router.get("/health/ready").handler(ctx -> ctx.response().end("READY"));

    vertx.createHttpServer()
      .requestHandler(router)
      .listen(config.httpPort())
      .onSuccess(server -> {
        System.setProperty("http.actualPort", String.valueOf(server.actualPort()));
        log.info("HTTP server started on port {}", server.actualPort());
        startPromise.complete();
      })
      .onFailure(err -> {
        log.error("Failed to start HTTP server", err);
        startPromise.fail(err);
      });
  }
}
