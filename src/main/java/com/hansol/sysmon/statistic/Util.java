package com.hansol.sysmon.statistic;

import java.io.File;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class Util {

	private static final Logger logger = LogManager.getLogger(Util.class);

	public static String getConfigPath(String fileName) {
		return System.getProperty("app.home") == null ? 
				String.join(File.separator, System.getProperty("user.dir"), "config", fileName) 
				: String.join(File.separator, System.getProperty("app.home"), "config", fileName);
	}

	public static void getSqlConf(Vertx vertx, String confFileName, Handler<JsonObject> aHandler) {

		ConfigRetrieverOptions options = new ConfigRetrieverOptions()
				.setScanPeriod(-1) // 주기적 스캔을 설정하지 않음...
				.addStore(new ConfigStoreOptions()
						.setType("file")
						.setFormat("yaml")
						.setConfig(new JsonObject().put("path", confFileName)));

		ConfigRetriever retriever = ConfigRetriever.create(vertx, options);

		retriever.getConfig(c -> {
			if (c.succeeded()) {
				aHandler.handle(c.result());
			} else {
				logger.error(c.cause().getMessage());
				aHandler.handle(null);
			}
		});
	}
}
