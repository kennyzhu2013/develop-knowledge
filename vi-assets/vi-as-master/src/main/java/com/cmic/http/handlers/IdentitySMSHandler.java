package com.cmic.http.handlers;

import com.cmic.http.HandlerHelper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import net.sf.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class IdentitySMSHandler implements HttpHandler {
    private static Logger logger = LoggerFactory.getLogger(IdentitySMSHandler.class);

    @Override
    public void handle(HttpExchange exchange) {
        JSONObject resp = new JSONObject();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody()));
            StringBuffer reqBuffer = new StringBuffer();
            String s = null;
            while ((s = reader.readLine()) != null) {
                reqBuffer.append(s);
            }

            JSONObject reqJson = JSONObject.fromObject(reqBuffer.toString());
            logger.info("[remindCaller], sms call back recv:{}", reqJson);

            resp.put("code", "000000");
            resp.put("desc", "succ");
            HandlerHelper.sendResponse(exchange, resp.toString());
        } catch (Exception e) {
            resp.put("code", "1001");
            resp.put("desc", "fail");
            try {
                HandlerHelper.sendResponse(exchange, resp.toString());
            } catch (IOException ex) {
                logger.error("TranslateTerminateHandler IOException ", ex);
            }
        }
    }
}
