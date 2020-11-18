package com.hansol.sysmon.statistic;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

public class MainVerticle extends AbstractVerticle {

	private static final Logger logger = LogManager.getLogger(MainVerticle.class);

	@Override
	public void start() throws Exception {
		Vertx vertx = Vertx.vertx(new VertxOptions()
				.setWorkerPoolSize(10)
				.setInternalBlockingPoolSize(10));

		vertx.deployVerticle(FileHandleVerticle.class.getName(), logger::warn);
		vertx.deployVerticle(DataAccessVerticle.class.getName(), logger::warn);
		vertx.deployVerticle(WebVerticle.class.getName(), logger::warn);
	}
}
