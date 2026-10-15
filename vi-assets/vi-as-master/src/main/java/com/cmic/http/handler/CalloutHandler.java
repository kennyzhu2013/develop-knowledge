package com.cmic.http.handler;

import com.cmic.service.BillIdService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import net.sf.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;

public class CalloutHandler implements HttpHandler {
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
            String fromNum = req.getString("fromNum");
            String toNum = req.getString("toNum");
            String sessionid = BillIdService.getInstance().nextId();

//            CalloutController controller = (CalloutController) ControllerManager.getInstance().createController(sessionid, Controller.CALLOUT);
//            controller.go(fromNum, toNum);


//            JSONObject req = JSONObject.fromObject(params.toString());


            exchange.sendResponseHeaders(200, 0);
            OutputStream os = exchange.getResponseBody();
            os.write("ok".getBytes());
            os.close();
        } catch (Exception e) {
            logger.error("", e);
        }
    }
}
