package com.cmic.http.handlers;

import com.cmic.bizcontroller.controllerImpl.NewTwoLegController;
import com.cmic.bizcontroller.controllerImpl.vod.plugin.PluginHttpHandler;
import com.cmic.http.HandlerHelper;
import com.cmic.http.vo.VodVo;
import com.cmic.sip.controller.base.ControllerManager;
import com.cmic.constant.VodConstant;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class VodStartHandler implements HttpHandler {
    private static Logger logger = LoggerFactory.getLogger(VodStartHandler.class);

    @Override
    public void handle(HttpExchange exchange) {
        String callId = "";
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody()));
            StringBuffer reqBuffer = new StringBuffer();
            String s = null;
            while ((s = reader.readLine()) != null) {
                reqBuffer.append(s);
            }

            JSONObject reqJson = JSONObject.fromObject(reqBuffer.toString());
            int direction = (int) reqJson.getOrDefault("direction", 0);
            String videoFile = (String) reqJson.getOrDefault("videoFile", "");
            callId = (String) reqJson.getOrDefault("callId", "");
            String resId = (String) reqJson.getOrDefault("resId", "");
            String uniCode = (String) reqJson.getOrDefault("uniCode", "");
            int mode = (int)reqJson.getOrDefault("mode", 0);
            String busCode = (String) reqJson.getOrDefault("busCode", "");
            logger.info("[{}] Vod play request:{}", callId, reqJson.toString());

            if ((direction < 1 || direction > 3) || StringUtils.isBlank(busCode) || StringUtils.isBlank(uniCode) ||
                    StringUtils.isBlank(callId) || Long.valueOf(callId) <= 0 || StringUtils.isBlank(resId) || (mode <= 0 || mode > 3)) {
                HandlerHelper.sendResponse(exchange, new VodVo(VodConstant.VOD.VOD_HTTP_CODE.PARAM_INVALID, VodConstant.VOD.VOD_HTTP_MSG.PARAM_INVALID).toString());
                return;
            }
            NewTwoLegController controller = (NewTwoLegController) ControllerManager.getInstance().getController(callId);
            if (controller == null) {
                HandlerHelper.sendResponse(exchange, new VodVo(VodConstant.VOD.VOD_HTTP_CODE.CALL_NOT_EXIST, VodConstant.VOD.VOD_HTTP_MSG.CALL_NOT_EXIST).toString());
                return;
            }

            VodVo vodVo = PluginHttpHandler.activateVod(reqJson, controller);
            HandlerHelper.sendResponse(exchange, vodVo.toString());

        } catch (Exception e) {
            logger.error("[{}] Vod play http request exception ", callId, e);
            try {
                HandlerHelper.sendResponse(exchange, new VodVo(VodConstant.VOD.VOD_HTTP_CODE.OTHER_REASON, VodConstant.VOD.VOD_HTTP_MSG.OTHER_REASON).toString());
            } catch (IOException ioException) {
                logger.error("[{}] Vod play http request send response IOException ", callId, ioException);
            }
        }
    }
}
