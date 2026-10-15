package com.cmic.http.handler;


import com.cmic.recording.mediaevent.CallBackEvent;
import com.cmic.recording.MediaManager;
import com.cmic.recording.mediaevent.UnknownEvent;
import com.cmic.service.Slf4jMDCService;
import com.cmic.util.DtmfLogger;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;

/**
 * 媒体回调入口
 * @author XXXXX
 */
public class MediaHandler implements HttpHandler {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public void handle(HttpExchange exchange) {

        String sessionId = "";
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody()));
            StringBuilder params = new StringBuilder();
            String s = null;
            while ((s = reader.readLine()) != null) {
                params.append(s);
            }
            CallBackEvent callBackEvent = MediaManager.objectMapper.readValue(params.toString(), CallBackEvent.class);
            DtmfLogger.info(sessionId, "handle media callbackEvent: {}", callBackEvent);
            sessionId = callBackEvent.getSessionId();
            Slf4jMDCService.putMDCCustomerCallID(sessionId);
            int seqId = callBackEvent.getSeqId();
            if(callBackEvent instanceof UnknownEvent){
                logger.info("sessionId:{} handleCallBack seqId:{} event:{} CallBackEvent is null", sessionId, seqId, callBackEvent.getEvent());
            }else {
                MediaManager.processMediaCallBack(sessionId, seqId, callBackEvent);
            }

            exchange.sendResponseHeaders(200, 0);
            OutputStream os = exchange.getResponseBody();
            os.write("{\"msg\":\"ok\"}".getBytes());
            os.close();
        } catch (Exception e) {
            logger.error("[{}] handle media callback error msg: ", sessionId, e);
        }finally {
            Slf4jMDCService.initMDCCustomerCallID();
        }
    }
}
