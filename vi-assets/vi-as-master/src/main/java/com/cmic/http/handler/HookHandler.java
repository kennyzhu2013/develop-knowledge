package com.cmic.http.handler;

import com.cmic.service.Slf4jMDCService;
import com.cmic.sip.controller.base.ControllerManager;
import com.cmic.sip.controller.base.Controller;
import com.cmic.sip.event.HttpRequestEvent;
import com.cmic.sip.leg.Hook;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import net.sf.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;

public class HookHandler implements HttpHandler {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public void handle(HttpExchange exchange) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody()));

            StringBuffer params = new StringBuffer();
            String s = null;
            while ((s = reader.readLine()) != null) {
                params.append(s);
            }
            logger.info("---------------");
            logger.info(params.toString());
            logger.info("---------------");
            JSONObject req = JSONObject.fromObject(params.toString());
            String sessionid = req.getString("tid");
            Slf4jMDCService.putMDCCustomerCallID(sessionid);
            String tid = req.getString("tid");
            String cmd = req.getString("cmd");

            logger.info("sessionid={} tid={} cmd={}", sessionid, tid, cmd);

            logger.info("controller.count={}", ControllerManager.getInstance().getTotalCount());
            Controller controller = ControllerManager.getInstance().getController(sessionid);
            if (controller != null) {
                Hook<HttpRequestEvent> hook = controller.findHttpRequestHook(tid);
                if (hook != null) {
                    hook.apply(new HttpRequestEvent(sessionid, cmd, req));
                } else {
                    logger.info("no hook tid={}", tid);
                }
            } else {
                logger.info("no controller sessionid={}", sessionid);
            }

            exchange.sendResponseHeaders(200, 0);
            OutputStream os = exchange.getResponseBody();
            os.write("ok".getBytes());
            os.close();
        } catch (Exception e) {
            logger.info("", e);
        }finally {
            Slf4jMDCService.initMDCCustomerCallID();
        }
    }
}
