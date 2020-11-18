package com.hansol.sysmon.statistic;

import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.LocalMap;

public class FileHandleVerticle extends AbstractVerticle {

	private final Logger logger = LogManager.getLogger(this.getClass());

	// AtomicLong size = new AtomicLong();
	static ConcurrentHashMap<String, Long> sizeInfo = new ConcurrentHashMap<String, Long>();

	static ConcurrentHashMap<String, Long> throttleInfo = new ConcurrentHashMap<String, Long>();
	private static final long realTimeThrottleMills = 3 * 60 * 1000;

	private LocalMap<String,JsonObject> realTimeMap = null;

	final String path = "C:/workspace_new/Hcheck-ydh/logs/agent/agent.log.LAPTOP-IRQOLSO6";

	@Override
	public void start() throws Exception {

		realTimeMap = vertx.sharedData().getLocalMap("realtime-static-map");

		getAsyncFileSize(size -> {
			sizeInfo.put("sizeinfo", size);
		});

		vertx.runOnContext(c -> {
			vertx.fileSystem().open(path, new OpenOptions(), result -> {
				if (result.succeeded()) {
					AsyncFile file = result.result();

					vertx.setPeriodic(10*1000, p -> {

						getAsyncFileSize(currSize -> {
							long readPos = sizeInfo.get("sizeinfo"); // size.get();
							int readLen = Long.valueOf(currSize).intValue() - Long.valueOf(readPos).intValue();

							logger.debug("pos:{}, len:{}", readPos, readLen);

							if (readLen > 0) {
								Buffer buff = Buffer.buffer(readLen); // TODO Require Buffer size limit 

								file.read(buff, 0, readPos, readLen, ar -> {
									if (ar.succeeded()) {
										final String changedStr = ar.result().toString();

										String[] arr = changedStr.split("\n");
										logger.info("[");

										JsonArray jsonArr = new JsonArray();
										for (String ele : arr) {
											if (ele.indexOf("[RECV]") != -1) {
												try {
													JsonObject data = new JsonObject(ele.substring(32));
													logger.info(">> {}", data.encode());

													String deviceId = data.getString("deviceid");

													realTimeMap.put(deviceId, data);

													if (!throttleInfo.containsKey(deviceId)) {
														throttleInfo.put(deviceId, System.currentTimeMillis());
														jsonArr.add(data);
													} else {
														if ( System.currentTimeMillis() > throttleInfo.get(deviceId) + realTimeThrottleMills ) {
															throttleInfo.put(deviceId, System.currentTimeMillis());
															jsonArr.add(data);
														} else {
															logger.debug("## skip....");
														}
													}

												} catch (DecodeException e) {
													logger.error(e.getMessage());
												}
											}
										}
										logger.info("]");

										if (jsonArr.size() > 0) {
											vertx.eventBus().send("statistic.save", jsonArr);
										}

									} else {
										logger.error("Failed to write: {}", ar.cause());
									}

									getAsyncFileSize(size -> {
										sizeInfo.put("sizeinfo", size);
									});

								});
							}
						});
					});
				} else {
					logger.error("Cannot open file {}", result.cause());
				}
			});
		});
	}

	private void getAsyncFileSize(Handler<Long> aHandler) {
		vertx.fileSystem().props(path, prop -> {
			if (prop.succeeded()) {
				aHandler.handle(prop.result().size());
			} else {
				aHandler.handle(0L);
			}
		});
	}
}
