package com.cmic.http;

import com.sun.net.httpserver.HttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;

public class HandlerHelper {

    private static Logger logger = LoggerFactory.getLogger(HandlerHelper.class);

    public static void sendResponse(HttpExchange t, String response) throws IOException {
        //logger.info("sendResponse response.length:{}",response.length());
        //logger.info("sendResponse utf8 response.length:{}",response.getBytes("UTF-8").length);
        //t.sendResponseHeaders(200, response.length());
        t.sendResponseHeaders(200, response.getBytes("UTF-8").length);
        t.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");

        OutputStream os = t.getResponseBody();
        os.write(response.getBytes("UTF-8"));
        os.close();
    }
}
