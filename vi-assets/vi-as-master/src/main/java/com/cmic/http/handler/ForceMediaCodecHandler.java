package com.cmic.http.handler;

import com.cmic.recording.MediaSession;
import com.cmic.service.Slf4jMDCService;
import com.cmic.sip.controller.base.Controller;
import com.cmic.sip.controller.base.ControllerManager;
import com.cmic.sip.event.HttpRequestEvent;
import com.cmic.sip.leg.Hook;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.print.attribute.standard.Media;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ForceMediaCodecHandler implements HttpHandler {
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
            if (!req.containsKey("forceMediaCodec")){
                exchange.sendResponseHeaders(200, 0);
                OutputStream os = exchange.getResponseBody();
                os.write("param error".getBytes());
                os.close();
                return;
            }
            String forceMedia = req.getString("forceMediaCodec");
            if (StringUtils.isBlank(forceMedia)){
                MediaSession.testSupportedCodec = null;
            }else {
                String[] split = forceMedia.split(",");
                MediaSession.testSupportedCodec = new HashSet<>(Arrays.asList(split));
            }

            exchange.sendResponseHeaders(200, 0);
            OutputStream os = exchange.getResponseBody();
            os.write(("have set to " + MediaSession.testSupportedCodec).getBytes());
            os.close();
        } catch (Exception e) {
            logger.info("", e);
        }finally {
            Slf4jMDCService.initMDCCustomerCallID();
        }
    }

}
