package com.cmic.http.handler;

import com.cmic.manager.VersionManager;
import com.cmic.sip.controller.base.ControllerManager;
import com.cmic.sip.engine.SipEngine;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import net.sf.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;

public class OpsHandler implements HttpHandler {
    private Logger logger = LoggerFactory.getLogger(getClass());


    @Override
    public void handle(HttpExchange exchange) {


        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody()));
            StringBuffer params = new StringBuffer();
            String s = null;
            while ((s = reader.readLine()) != null) {
                params.append(s);
            }
            String path = exchange.getRequestURI().getPath();
            System.out.println("========" + path);

            if ("/ops/status".equalsIgnoreCase(path)) {
                int version = VersionManager.getInstance().getVersion();
                int session = ControllerManager.getInstance().getTotalCount();
                int state = SipEngine.instance.getState();
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("version", version);
                jsonObject.put("session", session);
                jsonObject.put("state", state);
                sendResponse(exchange, jsonObject.toString());
                return;

            } else if ("/ops/pause".equalsIgnoreCase(path)) {
                SipEngine.instance.turnOnOff(false);
            } else if ("/ops/resume".equalsIgnoreCase(path)) {
                SipEngine.instance.turnOnOff(true);
            }

//            logger.info("-------------- statis -------------");
//            logger.info("getTotalCount={}", ControllerManager.getInstance().getTotalCount());
//            logger.info("-----------------------------------");


            JSONObject jsonObject = new JSONObject();
            jsonObject.put("res", "success");
            sendResponse(exchange, jsonObject.toString());
        } catch (Exception e) {
            logger.error("httpserver", e);
        }
    }

    private void sendResponse(HttpExchange t, String response) throws IOException {
        t.sendResponseHeaders(200, response.length());
        t.getResponseHeaders().set("Content-Type", "application/json");
        OutputStream os = t.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }
}
