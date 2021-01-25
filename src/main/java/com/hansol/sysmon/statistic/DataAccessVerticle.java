package com.hansol.sysmon.statistic;

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
	private static JDBCClient jdbcClient = null;
	private Properties prop = new Properties();

	@Override
	public void start() throws Exception {

		vertx.eventBus().<JsonArray>consumer("statistic.save", this::saveStatisticTx);

		try {
			final String confPath = Util.getConfigPath("db.properties");
			System.out.println("----------------------> confPath: " + confPath);
			prop.load(new FileReader(confPath));

			String dbmsType = config().getString("dbms-type");
			
			int idleTimeoutSec = Integer.parseInt(prop.getProperty(dbmsType + ".idle_timeout_sec"));
			logger.info("### idleTimeoutSec : {}", idleTimeoutSec);
			
			jdbcClient = JDBCClient.createShared(vertx, new JsonObject()
					.put("provider_class", "io.vertx.ext.jdbc.spi.impl.HikariCPDataSourceProvider")
					.put("jdbcUrl", prop.getProperty(dbmsType + ".url"))
					.put("driverClassName", prop.getProperty(dbmsType + ".driver"))
					.put("maximumPoolSize", Integer.parseInt(prop.getProperty(dbmsType + ".max_pool_size")))
					.put("username", prop.getProperty(dbmsType + ".username"))
					.put("password", prop.getProperty(dbmsType + ".password"))
					.put("idleTimeout", idleTimeoutSec * 1000) // idel_timeout_sec
					.put("minimumIdle", Integer.parseInt(prop.getProperty(dbmsType + ".min_pool_size")))
					);
		} catch (Exception e) {
			logger.error("jdbc client create failed : {}", e.getMessage());
		}
	}


	private void saveStatisticTx(Message<JsonArray> msg) {
		logger.debug("len:{}, data:{}", msg.body().size(), msg.body().encode());

		final JsonArray data = msg.body();
		final JsonObject queryObj = getMultiDbmsSql();

		jdbcClient.getConnection(connection -> {
			if (connection.succeeded()) {
				logger.debug("jdbc connection success");

				final SQLConnection conn = connection.result();

				String inParam = "(" + getMultiSqlInString(data) + ")";
				String selectSql = queryObj.getString("get-max-seq") + inParam + queryObj.getString("get-max-seq-tail");

				conn.query(selectSql, rs -> {
					if (rs.failed()) {
						logger.error("select error : {}", rs.cause().getMessage());
						return;
					}

					final List<JsonArray> seqList = rs.result().getResults();

					if (seqList.size() == 0) {
						for (Object entry : data) {
							JsonArray seqArr = new JsonArray();
							seqArr.add(((JsonObject)entry).getString("deviceid"));
							seqArr.add(1);
							seqList.add(seqArr);
						}
					}

					List<JsonObject> prepare = data.stream().map(m -> ((JsonObject)m)
							.put("save-seq", findSeq(((JsonObject)m).getString("deviceid"), seqList).getValue(1))
							)
							.collect(Collectors.toList());

					logger.debug("prepare : {}", prepare);

					List<JsonArray> saveData = new ArrayList<>();
					for (JsonObject obj : prepare) {
						JsonArray a = new JsonArray();
						if ("mysql".equals(config().getString("dbms-type"))) {
							a.add(obj.getString("deviceid"));
							a.add(obj.getValue("save-seq"));
							a.add(obj.getString("executetime"));
							a.add(obj.getValue("save-seq"));
							a.add(obj.getString("executetime"));
						} else {
							a.add(obj.getString("deviceid"));
							a.add(obj.getValue("save-seq"));
							a.add(obj.getString("executetime").substring(0, 19));
						}
						saveData.add(a);
					}

					logger.debug("saveData:{}", saveData.toString());

					// start a transaction
					startTx(conn, beginTrans -> {
						conn.batchWithParams(queryObj.getString("save-main"), saveData, ar1 -> {
							if (ar1.failed()) {
								logger.error(ar1.cause().getMessage());
								rollbackAndClose(conn, rc -> {
									throw new RuntimeException(ar1.cause());
								});

							} else {
								conn.batchWithParams(queryObj.getString("save-sub"), makeSubSaveData(prepare), ar2 -> {

									if (ar2.failed()) {
										logger.error(ar2.cause().getMessage());
										rollbackAndClose(conn, rc -> {
											throw new RuntimeException(ar2.cause());
										});

									} else {
										// commit data
										endTx(conn, commitTrans -> {
											conn.close(c -> {
												if (c.failed()) {
													logger.error(c.cause().getMessage());
												}
											});
										});
									}
								});
							}
						});
					});
				});

			} else {
				logger.error(connection.cause().getMessage());
			}
		});
	}

	private List<JsonArray> makeSubSaveData(List<JsonObject> prepare) {

		List<JsonArray> saveData = new ArrayList<JsonArray>();

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
					
					if (config().getBoolean("save-addtional")) {

						// diskTotal
						JsonArray l = new JsonArray();
						l.add(obj.getString("deviceid"));
						l.add(obj.getString("deviceip"));
						l.add(obj.getValue("save-seq"));
						l.add("diskTotal");
						l.add(String.valueOf(idx));
						l.add(diskinfo.getJsonObject(idx).getLong("tot")); 
						saveData.add(l);
						
						// diskUsed
						JsonArray m = new JsonArray();
						m.add(obj.getString("deviceid"));
						m.add(obj.getString("deviceip"));
						m.add(obj.getValue("save-seq"));
						m.add("diskUsed");
						m.add(String.valueOf(idx));
						m.add(diskinfo.getJsonObject(idx).getLong("used"));
						saveData.add(m);
					}
				}
			}
		}

		return saveData;
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
		logger.info("[TX] start tx...");
		conn.setAutoCommit(false, res -> {
			if (res.failed()) {
				conn.close(c -> {
					if (c.failed()) {
						logger.error("Conn close failed : {}", c.cause().getMessage());
					}
					
					throw new RuntimeException(res.cause());
				});
			}

			done.handle(null);
		});
	}

	private void endTx(SQLConnection conn, Handler<ResultSet> done) {
		logger.info("\n#################\n[TX] commit try...");
		conn.commit(res -> {
			if (res.failed()) {
				logger.error("  - commit error: {}", res.cause().getMessage());
				throw new RuntimeException(res.cause());
			}

			logger.info("\n[TX] commit end...\n#################");

			done.handle(null);
		});
	}

	@SuppressWarnings("unused")
	private void rollbackTx(SQLConnection conn, Handler<ResultSet> done) {
		conn.rollback(res -> {
			if (res.failed()) {
				throw new RuntimeException(res.cause());
			}

			done.handle(null);
		});
	}

	private void rollbackAndClose(SQLConnection conn, Handler<ResultSet> done) {
		logger.error("\n#################\n[ SQL Error --> TX Rollback Try");

		conn.rollback(res -> {
			if (res.failed()) {
				// throw new RuntimeException(res.cause());
				logger.error("\n  - TX Rollback failed... {}", res.cause().getMessage());
			}

			conn.close(c -> {
				if (c.failed()) {
					logger.error("\n  - Conn close failed : {}", c.cause().getMessage());
				} else {
					logger.error("\n  - TX rollbackAndClose end...]\n#################");
				}

				done.handle(null);
			});
		});
	}

	private JsonObject getMultiDbmsSql() {
		return "mysql".equals(config().getString("dbms-type")) ? 
				config().getJsonObject("sql-service").getJsonObject("mysql") 
				: config().getJsonObject("sql-service").getJsonObject("oracle");
	}
}
