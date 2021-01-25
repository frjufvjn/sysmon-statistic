package com.hansol.sysmon.statistic;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
	static ConcurrentHashMap<String, Integer> distributedIdxInfo = new ConcurrentHashMap<String, Integer>(); 
	
	private LocalMap<String,JsonObject> realTimeMap = null;
	private String path = "";
	private static int saveThrottleCnt = 5;
	private static int sysGabSec = 5;

	@Override
	public void start() throws Exception {

		path = config().getString("read-log-file-path");
		saveThrottleCnt = config().getInteger("save-throttle-cnt", saveThrottleCnt);
		sysGabSec = config().getInteger("sys-gab-sec", sysGabSec);

		realTimeMap = vertx.sharedData().getLocalMap("realtime-static-map");

		fileRead();
	}

	private void fileRead() {

		/**
		 * @see - 아래와 같은 이유로 50초 이상 초과하지 않게 설정 해야함.
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
															distributedIdxInfo.put(deviceId, throttleInfo.size());
															
															int distributedIdx = distributedIdxInfo.get(deviceId) % 10;
															logger.info("deviceid:{}, distributedIdx:{}", deviceId, distributedIdx);
															

															if (distributedIdx == 0) {
																throttleInfo.put(deviceId, System.currentTimeMillis());
																jsonArr.add(data);
															} else {
																throttleInfo.put(deviceId, System.currentTimeMillis() + (distributedIdx * 60_000));
															}
															
														} else {
															if ( System.currentTimeMillis() > throttleInfo.get(deviceId) + (saveThrottleMills - (sysGabSec*1000)) ) {
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
												// vertx.eventBus().send("statistic.save", jsonArr);
												distributedSaveDispatch(jsonArr);
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

	private void distributedSaveDispatch(JsonArray jsonArr) {

		int remainder = jsonArr.size() % saveThrottleCnt;
		int saveLoopCnt = jsonArr.size() / saveThrottleCnt + (remainder > 0 ? 1 : 0);

		logger.info("saveLoopCnt : {}", saveLoopCnt);

		for (int idx=0; idx<saveLoopCnt; idx++) {
			int startIdx = idx*saveThrottleCnt;
			int endIdx;
			if (saveLoopCnt == (idx+1)) {
				if (remainder > 0) {
					endIdx = startIdx + remainder;
				} else {
					endIdx = startIdx + saveThrottleCnt;
				}
			} else {
				endIdx = startIdx + saveThrottleCnt;
			}

			logger.info("start:{}, end:{}", startIdx, endIdx);

			List<Object> rangedArr = IntStream.range(startIdx, endIdx)
					.mapToObj(i -> jsonArr.getValue(i))
					.collect(Collectors.toList());

			// System.out.println(new JsonArray(rangedArr).encode());
			vertx.eventBus().send("statistic.save", new JsonArray(rangedArr));
		}
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
