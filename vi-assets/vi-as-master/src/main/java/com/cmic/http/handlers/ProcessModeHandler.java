package com.cmic.http.handlers;

import com.cmic.http.HandlerHelper;
import com.cmic.sip.engine.DtmfEngine;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import net.sf.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 *
 *
 * @author XH
 * @version 1.0
 * @date 2026-06-08
 */
public class ProcessModeHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(ProcessModeHandler.class);

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
            logger.info("[ProcessModeHandler], recv:{}", reqJson);
            String msg = "";
            if(reqJson.containsKey("startMode")){
                int startMode = reqJson.getInt("startMode");
                if(startMode == 0 || startMode == 1 ||  startMode == 2){
                    msg = DtmfEngine.getInstance().switchStartMode(startMode);
                } else {
                    msg = "startMode invalid";
                }
            } else {
                msg = "Missing necessary fields";
            }
            resp.put("code", "000000");
            resp.put("desc", msg);
            HandlerHelper.sendResponse(exchange, resp.toString());
        } catch (Exception e) {
            resp.put("code", "1001");
            resp.put("desc", "fail");
            try {
                HandlerHelper.sendResponse(exchange, resp.toString());
            } catch (IOException ex) {
                logger.error("ProcessModeHandler IOException ", ex);
            }
        }
    }
}
