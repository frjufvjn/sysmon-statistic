package com.hansol.sysmon.statistic;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

public class MainVerticle extends AbstractVerticle {

	private static final Logger logger = LogManager.getLogger(MainVerticle.class);
	private static Properties prop = new Properties();

	@Override
	public void start() throws Exception {

		final String confPath = Util.getConfigPath("db.properties");
		System.out.println("----------------------> confPath: " + confPath);
		try {
			prop.load(new FileReader(confPath));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
		File f = new File(prop.getProperty("common.logpath", "C:/Temp/log4j2.xml"));
		ctx.setConfigLocation(f.toURI());

		Vertx vertx = Vertx.vertx(new VertxOptions()
				.setWorkerPoolSize(10)
				.setInternalBlockingPoolSize(10));

		Util.getSqlConf(vertx, Util.getConfigPath("config.yml"), ar -> {
			if (ar != null) {
				logger.debug("config : {}", ar.encodePrettily());

				DeploymentOptions depOpts = new DeploymentOptions().setConfig(ar);

				vertx.deployVerticle(FileHandleVerticle.class.getName(), depOpts, logger::warn);
				vertx.deployVerticle(DataAccessVerticle.class.getName(), depOpts, logger::warn);
				vertx.deployVerticle(WebApiVerticle.class.getName(), logger::warn);
			}
		});
	}
}
