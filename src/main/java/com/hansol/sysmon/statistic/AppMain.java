package com.hansol.sysmon.statistic;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

public class AppMain {

	private static final Logger logger = LogManager.getLogger(AppMain.class);
	
	public static void main(String[] args) {
		
		new AppMain().startup();
	}

	private void startup() {
		// verticle launcher
		/*Launcher.executeCommand("version", args);
				Launcher.executeCommand("run", new String[]{TestVerticle.class.getName()});*/

		Vertx vertx = Vertx.vertx(new VertxOptions()
				.setWorkerPoolSize(10)
				.setInternalBlockingPoolSize(10));

		vertx.deployVerticle(FileHandleVerticle.class.getName(), logger::warn);
		vertx.deployVerticle(DataAccessVerticle.class.getName(), logger::warn);
		vertx.deployVerticle(WebVerticle.class.getName(), logger::warn);
	}
}
