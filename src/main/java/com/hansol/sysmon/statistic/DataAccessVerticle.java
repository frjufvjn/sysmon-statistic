package com.hansol.sysmon.statistic;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;

public class DataAccessVerticle extends AbstractVerticle {

	private final Logger logger = LogManager.getLogger(this.getClass());
	private static JDBCClient client = null;
	private Properties prop = new Properties();

	// TODO extract config file
	private final String SQL_GET_MAX_SEQ = "SELECT device_id, IFNULL(MAX(last_seq),0) + 1 last_seq \r\n" + 
			"FROM t_agnt_value \r\n" + 
			"WHERE device_id in \r\n"
			;
	private final String SQL_GET_MAX_SEQ_TAIL = "GROUP BY device_id";

	private final String SQL_SAVE_MAIN = "INSERT INTO t_agnt_value (device_id, last_updated, last_seq, data_gathered)\r\n" + 
			"			VALUES (?, now(), ?, str_to_date(?, '%Y-%m-%d %H:%i:%s.%f'))\r\n" + 
			"			ON DUPLICATE KEY \r\n" + 
			"				UPDATE\r\n" + 
			"					last_seq = ?,\r\n" + 
			"					last_updated = now(), \r\n" + 
			"					data_gathered = str_to_date(?, '%Y-%m-%d %H:%i:%s.%f')";

	private final String SQL_SAVE_SUB = "INSERT INTO t_agnt_value_min (device_id, device_ip, seq, crit_name, crit_sub_name, value, reg_time)\r\n" + 
			"			VALUES (\r\n" + 
			"				?,\r\n" + 
			"				?,\r\n" + 
			"				?,\r\n" + 
			"				?,\r\n" + 
			"				?,\r\n" + 
			"				?,\r\n" + 
			"				NOW()\r\n" + 
			"			)";

	@Override
	public void start() throws Exception {
		vertx.eventBus().<JsonArray>consumer("statistic.save", this::save);

		final String confPath = System.getProperty("app.home") == null ? String.join(File.separator, System.getProperty("user.dir"), "config", "db.properties") : String.join(File.separator, System.getProperty("app.home"), "config", "db.properties");
		prop.load(new FileReader(confPath));

		client = JDBCClient.createShared(vertx, new JsonObject()
				.put("provider_class", "io.vertx.ext.jdbc.spi.impl.HikariCPDataSourceProvider")
				.put("jdbcUrl", prop.getProperty("db.url"))
				.put("driverClassName", prop.getProperty("db.driver"))
				.put("maximumPoolSize", Integer.parseInt(prop.getProperty("db.max_pool_size")))
				.put("username", prop.getProperty("db.username"))
				.put("password", prop.getProperty("db.password"))
				.put("idleTimeout", 600000)
				.put("minimumIdle", Integer.parseInt(prop.getProperty("db.min_pool_size")))
				);
	}

	private void save(Message<JsonArray> msg) {
		logger.debug("len:{}, data:{}", msg.body().size(), msg.body().encode());

		final JsonArray data = msg.body();

		client.getConnection(connection -> {
			if (connection.succeeded()) {
				logger.debug("jdbc connection success");

				final SQLConnection conn = connection.result();

				String inParam = "(" + getMultiSqlInString(data) + ")";

				List<JsonArray> saveData = new ArrayList<>();
				conn.query(SQL_GET_MAX_SEQ + inParam + SQL_GET_MAX_SEQ_TAIL, rs -> {
					if (rs.failed()) {
						logger.error(rs.cause().getMessage());
						return;
					}

					final List<JsonArray> seqList = rs.result().getResults();

					List<JsonObject> prepare = data.stream().map(m -> ((JsonObject)m)
							.put("save-seq", findSeq(((JsonObject)m).getString("deviceid"), seqList).getValue(1))
							)
							.collect(Collectors.toList());

					logger.debug("prepare : {}", prepare);

					for (JsonObject obj : prepare) {
						JsonArray a = new JsonArray();
						a.add(obj.getString("deviceid"));
						a.add(obj.getValue("save-seq"));
						a.add(obj.getString("executetime"));
						a.add(obj.getValue("save-seq"));
						a.add(obj.getString("executetime"));
						saveData.add(a);
					}

					logger.debug("saveData:{}", saveData.toString());

					// start a transaction
					startTx(conn, beginTrans -> {
						conn.batchWithParams(SQL_SAVE_MAIN, saveData, ar1 -> {

							// device_id, device_ip, seq, crit_name, crit_sub_name, value
							saveData.clear();
							for (JsonObject obj : prepare) {

								// cpuUsage
								JsonArray a = new JsonArray();
								a.add(obj.getString("deviceid"));
								a.add(obj.getString("deviceip"));
								a.add(obj.getValue("save-seq"));
								a.add("cpuUsage");
								a.add("");
								a.add(String.format("%.3f", obj.getDouble("cpu")));
								saveData.add(a);

								// memoryTotal
								JsonArray b = new JsonArray();
								b.add(obj.getString("deviceid"));
								b.add(obj.getString("deviceip"));
								b.add(obj.getValue("save-seq"));
								b.add("memoryTotal");
								b.add("");
								b.add(String.valueOf(obj.getLong("rmemtot")));
								saveData.add(b);

								// memoryFree
								JsonArray c = new JsonArray();
								c.add(obj.getString("deviceid"));
								c.add(obj.getString("deviceip"));
								c.add(obj.getValue("save-seq"));
								c.add("memoryFree");
								c.add("");
								c.add(String.valueOf(obj.getLong("rmemfree")));
								saveData.add(c);

								// memoryUsage
								JsonArray d = new JsonArray();
								d.add(obj.getString("deviceid"));
								d.add(obj.getString("deviceip"));
								d.add(obj.getValue("save-seq"));
								d.add("memoryUsage");
								d.add("");
								d.add(String.format("%.3f", obj.getDouble("rmem")));
								saveData.add(d);

								// swapTotal
								JsonArray e = new JsonArray();
								e.add(obj.getString("deviceid"));
								e.add(obj.getString("deviceip"));
								e.add(obj.getValue("save-seq"));
								e.add("swapTotal");
								e.add("");
								e.add(String.valueOf(obj.getLong("smemtot")));
								saveData.add(e);

								// swapFree
								JsonArray f = new JsonArray();
								f.add(obj.getString("deviceid"));
								f.add(obj.getString("deviceip"));
								f.add(obj.getValue("save-seq"));
								f.add("swapFree");
								f.add("");
								f.add(String.valueOf(obj.getLong("smemfree")));
								saveData.add(f);

								// swapUsage
								JsonArray g = new JsonArray();
								g.add(obj.getString("deviceid"));
								g.add(obj.getString("deviceip"));
								g.add(obj.getValue("save-seq"));
								g.add("swapUsage");
								g.add("");
								g.add(String.format("%.3f", obj.getDouble("smem")));
								saveData.add(g);

								// osName
								JsonArray h = new JsonArray();
								h.add(obj.getString("deviceid"));
								h.add(obj.getString("deviceip"));
								h.add(obj.getValue("save-seq"));
								h.add("osName");
								h.add("");
								h.add(obj.getString("archtype"));
								saveData.add(h);

								// companyName
								JsonArray i = new JsonArray();
								i.add(obj.getString("deviceid"));
								i.add(obj.getString("deviceip"));
								i.add(obj.getValue("save-seq"));
								i.add("companyName");
								i.add("");
								i.add("shcard");
								saveData.add(i);

								JsonArray diskinfo = obj.getJsonArray("diskinfo");
								if ( diskinfo != null && diskinfo.size() > 0 ) {

									for (int idx=0; idx < diskinfo.size(); idx++) {

										// diskName
										JsonArray j = new JsonArray();
										j.add(obj.getString("deviceid"));
										j.add(obj.getString("deviceip"));
										j.add(obj.getValue("save-seq"));
										j.add("diskName");
										j.add(String.valueOf(idx));
										j.add(diskinfo.getJsonObject(idx).getString("path")); 
										saveData.add(j);

										// diskUsage
										JsonArray k = new JsonArray();
										k.add(obj.getString("deviceid"));
										k.add(obj.getString("deviceip"));
										k.add(obj.getValue("save-seq"));
										k.add("diskUsage");
										k.add(String.valueOf(idx));
										k.add(String.format("%.3f", diskinfo.getJsonObject(idx).getDouble("usedPercent"))); 
										saveData.add(k);
									}
								}
							}

							conn.batchWithParams(SQL_SAVE_SUB, saveData, ar2 -> {
								// commit data
								endTx(conn, commitTrans -> {
									conn.close(c -> {
										if (c.failed()) {
											logger.error(c.cause().getMessage());
										}
									});
								});
							});
						});
					});
				});

			} else {
				logger.error(connection.cause().getMessage());
			}
		});
	}

	private JsonArray findSeq(String deviceId, List<JsonArray> seqList) {
		return seqList.stream()
				.filter(f -> deviceId.equals(f.getList().get(0).toString()))
				.findAny()
				.orElse(null);
	}

	private String getMultiSqlInString(JsonArray jarr) {
		return jarr.stream()
				.map(m -> "'" + ((JsonObject)m).getString("deviceid") + "'")
				.collect(Collectors.joining(","));
	}

	private void startTx(SQLConnection conn, Handler<ResultSet> done) {
		conn.setAutoCommit(false, res -> {
			if (res.failed()) {
				throw new RuntimeException(res.cause());
			}

			done.handle(null);
		});
	}

	private void endTx(SQLConnection conn, Handler<ResultSet> done) {
		conn.commit(res -> {
			if (res.failed()) {
				throw new RuntimeException(res.cause());
			}

			done.handle(null);
		});
	}
}
