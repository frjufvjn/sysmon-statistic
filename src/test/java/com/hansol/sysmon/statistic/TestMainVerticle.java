package com.hansol.sysmon.statistic;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class TestMainVerticle {

	/*@BeforeEach
	void deploy_verticle(Vertx vertx, VertxTestContext testContext) {
		vertx.deployVerticle(new MainVerticle(), testContext.succeeding(id -> testContext.completeNow()));
	}*/

	@Test
	void verticle_deployed(Vertx vertx, VertxTestContext testContext) throws Throwable {
		testContext.completeNow();
	}
	
	@Test
	void test1() {
		String jsonTxt1 = "{\"type\":\"common\",\"conftime\":1586226250363,\"executetime\":\"2020-11-18 23:16:59.542\",\"deviceid\":\"server03\",\"deviceip\":\"192.168.219.104\",\"vip\":\"\",\"isHA\":false,\"isProcessAlarm\":false,\"archtype\":\"LINUX\",\"cpu\":8.673509998068514,\"rmem\":50.19768956315113,\"rmemtot\":17089323008,\"rmemfree\":8310870016,\"smem\":0.18478234608968097,\"smemtot\":51539607552,\"smemfree\":51444371456,\"diskinfo\":null}";
		String jsonTxt2 = "{\"type\":\"common\",\"conftime\":1586226250363,\"executetime\":\"2020-11-18 23:16:59.542\",\"deviceid\":\"server03\",\"deviceip\":\"192.168.219.104\",\"vip\":\"\",\"isHA\":false,\"isProcessAlarm\":false,\"archtype\":\"LINUX\",\"cpu\":8.673509998068514,\"rmem\":50.19768956315113,\"rmemtot\":17089323008,\"rmemfree\":8310870016,\"smem\":0.18478234608968097,\"smemtot\":51539607552,\"smemfree\":51444371456,\"diskinfo\":[]}";
		String jsonTxt3 = "{\"type\":\"common\",\"conftime\":1586226250363,\"executetime\":\"2020-11-18 23:16:59.542\",\"deviceid\":\"server03\",\"deviceip\":\"192.168.219.104\",\"vip\":\"\",\"isHA\":false,\"isProcessAlarm\":false,\"archtype\":\"LINUX\",\"cpu\":8.673509998068514,\"rmem\":50.19768956315113,\"rmemtot\":17089323008,\"rmemfree\":8310870016,\"smem\":0.18478234608968097,\"smemtot\":51539607552,\"smemfree\":51444371456,\"diskinfo\":[{\"path\":\"/\",\"usedPercent\":0},{\"path\":\"/dev\",\"usedPercent\":0},{\"path\":\"/run\",\"usedPercent\":0},{\"path\":\"/run/lock\",\"usedPercent\":0},{\"path\":\"/run/shm\",\"usedPercent\":0},{\"path\":\"/run/user\",\"usedPercent\":0},{\"path\":\"/sys/fs/cgroup\",\"usedPercent\":0},{\"path\":\"/mnt/c\",\"usedPercent\":0}]}";
		
		JsonObject o1 = new JsonObject(jsonTxt1);
		assertTrue(o1.getJsonArray("diskinfo") == null);
		
		if ( o1.getJsonArray("diskinfo") == null || o1.getJsonArray("diskinfo").size() == 0 ) {
			System.out.println("true1");
		}
		
		JsonObject o2 = new JsonObject(jsonTxt2);
		assertTrue(o2.getJsonArray("diskinfo").size() == 0);
		
		if ( o2.getJsonArray("diskinfo") == null || o2.getJsonArray("diskinfo").size() == 0 ) {
			System.out.println("true2");
		}
		
		JsonObject o3 = new JsonObject(jsonTxt3);
		if ( o3.getJsonArray("diskinfo") != null && o3.getJsonArray("diskinfo").size() > 0 ) {
			System.out.println("true3");
		}
	}
}
