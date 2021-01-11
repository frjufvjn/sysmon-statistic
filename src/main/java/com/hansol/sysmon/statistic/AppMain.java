package com.hansol.sysmon.statistic;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

public class AppMain {

	private static final Logger logger = LogManager.getLogger(AppMain.class);
	private static Properties prop = new Properties();
	
	public static void main(String[] args) {

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
		
		new AppMain().startup();
		/*
		String head = "curl -k -X POST -d packet=\"";
		String packet = "{\"type\":\"common\",\"conftime\":0,\"executetime\":\"2020-10-19 10:36:36.708\",\"deviceid\":\"pds01\",\"deviceip\":\"10.1.12.52\",\"vip\":\"\",\"isHA\":true,\"isProcessAlarm\":false,\"archtype\":\"LINUX\",\"cpu\":5.487623762376928,\"rmem\":45.360503025024215,\"rmemtot\":17089323008,\"rmemfree\":9137512448,\"smem\":0,\"smemtot\":51539607552,\"smemfree\":51539607552,\"diskinfo\":[{\"path\":\"/\",\"usedPercent\":0},{\"path\":\"/dev\",\"usedPercent\":0},{\"path\":\"/run\",\"usedPercent\":0},{\"path\":\"/run/lock\",\"usedPercent\":0},{\"path\":\"/run/shm\",\"usedPercent\":0},{\"path\":\"/run/user\",\"usedPercent\":0},{\"path\":\"/sys/fs/cgroup\",\"usedPercent\":0},{\"path\":\"/mnt/c\",\"usedPercent\":0}]}";
		String tail = " https://127.0.0.1:8443/hcheck/integrate/sysmon.jsp";
		
		
		
		for (int i=1; i<=150; i++) {
			
			String deviceIdx = i < 10 ? "0"+i : ""+i;
			JsonObject p = new JsonObject(packet)
					.put("deviceid", "server" + deviceIdx)
					.put("isHA", false)
					;
			
			System.out.println(head + p.encode().replace("\"", "\\\"") + "\"" + tail);
		}*/
	}

	private void startup() {
		// verticle launcher
		/*Launcher.executeCommand("version", args);
				Launcher.executeCommand("run", new String[]{TestVerticle.class.getName()});*/

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
				vertx.deployVerticle(UploadVerticle.class.getName(), logger::warn);
			}
		});
	}


}
