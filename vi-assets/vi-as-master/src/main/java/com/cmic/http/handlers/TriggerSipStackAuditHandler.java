package com.cmic.http.handlers;

import com.cmic.service.ScheduleService;
import com.cmic.util.DevHelp;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import gov.nist.javax.sip.stack.StackAuditMonitor;
import net.sf.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyDescriptor;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author XXXXX
 * @date 2023/07/21
 */
public class TriggerSipStackAuditHandler implements HttpHandler {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final String timeParams = "sip_stack_audit_mins";

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody()));
        StringBuilder params = new StringBuilder();
        String s = null;
        while ((s = reader.readLine()) != null) {
            params.append(s);
        }

        JSONObject req = JSONObject.fromObject(params.toString());
        try {
            if (!req.containsKey(timeParams)) {
                exchange.sendResponseHeaders(400, 0);
                OutputStream os = exchange.getResponseBody();
                os.write("no sip_stack_audit_mins params".getBytes());
                os.close();
                return;
            }
            long checkAliveMinutes = req.getLong(timeParams);
            if (checkAliveMinutes < 20) {
                logger.error("checkAliveMinutes is lower than 20 , {}", checkAliveMinutes);
                exchange.sendResponseHeaders(400, 0);
                OutputStream os = exchange.getResponseBody();
                os.write("checkAliveMinutes is lower than 20".getBytes());
                os.close();
                return;
            }
            long period = TimeUnit.MILLISECONDS.convert(checkAliveMinutes, TimeUnit.MINUTES);
            StackAuditMonitor.triggerAuditStack(period);
        } catch (Exception e) {
            logger.error("Error happen while setting DevHelp property", e);
            throw new RuntimeException(e);
        }
        exchange.sendResponseHeaders(200, 0);
        OutputStream os = exchange.getResponseBody();
        os.write("ok".getBytes());
        os.close();
    }
}
