package com.hansol.sysmon.statistic;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;

public class WebApiVerticle extends AbstractVerticle {

	private final Logger logger = LogManager.getLogger(this.getClass());

	private LocalMap<String,JsonObject> realTimeMap = null;

	@Override
	public void start() throws Exception {
		realTimeMap = vertx.sharedData().getLocalMap("realtime-static-map");

		vertx.executeBlocking(promise -> {
			startupHttpServer(promise);
		}, result -> {
			if (result.failed()) logger.error(result.cause().getMessage());
		});
	}

	private void startupHttpServer(Future<Object> promise) {
		/**
		 * @description HTTP Router Setting
		 * */
		final Router router = Router.router(vertx);
		router.route().handler(StaticHandler.create().setCachingEnabled(false));


		router.route("/api/*")
		.handler(BodyHandler.create());

		router.post("/api/device-performance").handler(this::getStatisticApi);


		/**
		 * @description Create HTTP Server (RequestHandler & WebSocketHandler)
		 */
		final HttpServer httpServer = vertx.createHttpServer();

		int httpServerPort = config().getInteger("http-port", 28080);

		httpServer.exceptionHandler(ex -> {
			logger.error(ex.getMessage());
		});

		// RequestHandler
		httpServer.requestHandler(router::accept);

		// http listen
		httpServer.listen(httpServerPort, res -> {
			if (res.failed()) {
				promise.fail(res.cause());
			} else {
				promise.complete();
				logger.warn("Server listening at: http://localhost:{}, hashcode:{}", httpServerPort, vertx.getOrCreateContext().hashCode());
			}
		});
	}

	// curl -i -X POST -d {\"deviceid\":\"server11\"} localhost:28080/api/device-performance
	private void getStatisticApi(RoutingContext ctx) {
		String deviceId = ctx.getBodyAsJson().getString("deviceid");

		if (realTimeMap.containsKey(deviceId)) {
			ctx.response().end(realTimeMap.get(deviceId)
					.put("ResultCode", "0")
					.put("ResultMessage", "success")
					.encode());
		} else {
			ctx.response().end(new JsonObject()
					.put("ResultCode", "-1")
					.put("ResultMessage", "no data")
					.encode());
		}
	}
}
