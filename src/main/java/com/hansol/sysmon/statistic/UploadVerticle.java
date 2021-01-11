package com.hansol.sysmon.statistic;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.AbstractVerticle;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

/*
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class UploadVerticle extends AbstractVerticle {

	private final Logger logger = LogManager.getLogger(this.getClass());

	@Override
	public void start() throws Exception {

		Router router = Router.router(vertx);

		// Enable multipart form data parsing
		router.route().handler(BodyHandler.create().setUploadsDirectory("uploads"));

		router.route("/").handler(routingContext -> {
			routingContext.response().putHeader("content-type", "text/html").end(
					"<form action=\"/form\" method=\"post\" enctype=\"multipart/form-data\">\n" +
							"    <div>\n" +
							"        <label for=\"name\">Select a file:</label>\n" +
							"        <input type=\"file\" name=\"file\" />\n" +
							"    </div>\n" +
							"    <div class=\"button\">\n" +
							"        <button type=\"submit\">Send</button>\n" +
							"    </div>" +
							"</form>"
					);
		});

		// handle the form
		router.post("/form").handler(ctx -> {
			ctx.response().putHeader("Content-Type", "text/plain");

			ctx.response().setChunked(true);

			for (FileUpload f : ctx.fileUploads()) {
				System.out.println("f");
				ctx.response().write("Filename: " + f.fileName());
				ctx.response().write("\n");
				ctx.response().write("Size: " + f.size());
			}

			ctx.response().end();
		});

		vertx.createHttpServer().requestHandler(router::accept).listen(38080, res -> {
			if (res.failed()) {
				logger.error(res.cause().getMessage());
			} else {
				logger.warn("Server listening at: http://localhost:{}, hashcode:{}", 38080, vertx.getOrCreateContext().hashCode());
			}
		});
	}
}
