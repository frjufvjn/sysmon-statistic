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

	static ConcurrentHashMap<String, Long> sizeInfo = new ConcurrentHashMap<String, Long>();
	static ConcurrentHashMap<String, Long> throttleInfo = new ConcurrentHashMap<String, Long>();
	private LocalMap<String,JsonObject> realTimeMap = null;
	private String path = "";

	@Override
	public void start() throws Exception {

		path = config().getString("read-log-file-path");

		realTimeMap = vertx.sharedData().getLocalMap("realtime-static-map");

		fileRead();
	}

	private void fileRead() {

		/**
		 * @see - 아래와 같은 이유로 50초 이상 초과하지 않게 설정 해야함.
		 * 	<li>저장처리는 한번의 트랜잭션당 장비당 1개씩 제한 TODO 혹시라도 모르니 2개이상이 올때를 대비해 구현해야할듯... 
		 * 	<li>메모리 제한, 한번에 읽어들이는 양을 많이 소요하지 않기 위함
		 * */
		final long pollingTimeMills = config().getInteger("read-inteval-sec", 20) * 1000;
		final long saveThrottleMills = config().getInteger("save-throttle-min", 3) * 60 * 1000;

		getAsyncFileSize(size -> {
			sizeInfo.put("sizeinfo", size);
		});

		vertx.runOnContext(c -> {
			vertx.fileSystem().open(path, new OpenOptions().setRead(true), result -> {
				if (result.succeeded()) {
					AsyncFile file = result.result();

					vertx.setPeriodic(pollingTimeMills, period -> {

						getAsyncFileSize(currSize -> {
							long readPos = sizeInfo.get("sizeinfo"); // size.get();
							int readLen = Long.valueOf(currSize).intValue() - Long.valueOf(readPos).intValue();

							logger.debug("pos:{}, len:{}", readPos, readLen);

							if (readLen != 0) {

								if (readLen < 0) {
									logger.warn("Log file rolling event --> pos:{}, len:{}", readPos, readLen);
									file.close(mClosed -> {
										if (mClosed.succeeded()) {
											vertx.cancelTimer(period);

											// 재귀호출
											fileRead();
										}
									});

								} else {

									Buffer buff = Buffer.buffer(readLen); // TODO Require Buffer size limit 

									file.read(buff, 0, readPos, readLen, ar -> {
										if (ar.succeeded()) {
											final String changedStr = ar.result().toString();

											String[] arr = changedStr.split("\n");
											logger.debug("[");

											JsonArray jsonArr = new JsonArray();
											for (String ele : arr) {
												final int acceptableIdx = ele.indexOf("[RECV]");
												if (acceptableIdx != -1) {
													try {
														JsonObject data = new JsonObject(ele.substring(acceptableIdx + 7));
														logger.debug(">> {}", data.encode());

														String deviceId = data.getString("deviceid");

														realTimeMap.put(deviceId, data);

														if (!throttleInfo.containsKey(deviceId)) {
															throttleInfo.put(deviceId, System.currentTimeMillis());
															jsonArr.add(data);
														} else {
															if ( System.currentTimeMillis() > throttleInfo.get(deviceId) + saveThrottleMills ) {
																throttleInfo.put(deviceId, System.currentTimeMillis());
																jsonArr.add(data);
															} else {
																logger.debug("## skip....");
															}
														}

													} catch (DecodeException e) {
														logger.error(e.getMessage());
													}
												} else {
													logger.warn("unacceptable string --> {}", ele);
												}
											}
											logger.debug("]");

											if (jsonArr.size() > 0) {
												logger.info("save dispatch size: {}", jsonArr.size());
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
