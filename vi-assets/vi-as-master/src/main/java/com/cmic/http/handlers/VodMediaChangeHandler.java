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

public class VodMediaChangeHandler implements HttpHandler {
    private static Logger logger = LoggerFactory.getLogger(VodMediaChangeHandler.class);

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
            String newMedia = (String) reqJson.getOrDefault("audioCode", "");
            callId = (String) reqJson.getOrDefault("callId", "");
            logger.info("[{}] VodMediaChangeHandler request:{}", callId, reqJson.toString());

            if (StringUtils.isBlank(newMedia) || Long.valueOf(callId) <= 0) {
                HandlerHelper.sendResponse(exchange, new VodVo(VodConstant.VOD.VOD_HTTP_CODE.PARAM_INVALID, VodConstant.VOD.VOD_HTTP_MSG.PARAM_INVALID).toString());
                return;
            }

            if (!newMedia.equalsIgnoreCase("amr") && !newMedia.equalsIgnoreCase("amr-wb")
                    && !newMedia.equalsIgnoreCase("pcma") && !newMedia.equalsIgnoreCase("pcmu")) {
                HandlerHelper.sendResponse(exchange, new VodVo(VodConstant.VOD.VOD_HTTP_CODE.PARAM_INVALID, VodConstant.VOD.VOD_HTTP_MSG.PARAM_INVALID).toString());
                return;
            }

            NewTwoLegController controller = (NewTwoLegController) ControllerManager.getInstance().getController(callId);
            if (controller == null) {
                HandlerHelper.sendResponse(exchange, new VodVo(VodConstant.VOD.VOD_HTTP_CODE.CALL_NOT_EXIST, VodConstant.VOD.VOD_HTTP_MSG.CALL_NOT_EXIST).toString());
                return;
            }

            VodVo vodVo = PluginHttpHandler.changeVodAudioCode(reqJson, controller);
            HandlerHelper.sendResponse(exchange, vodVo.toString());

        } catch (Exception e) {
            logger.error("[{}] VVodMediaChangeHandler request exception ", callId, e);
            try {
                HandlerHelper.sendResponse(exchange, new VodVo(VodConstant.VOD.VOD_HTTP_CODE.OTHER_REASON, VodConstant.VOD.VOD_HTTP_MSG.OTHER_REASON).toString());
            } catch (IOException ioException) {
                logger.error("[{}] VodMediaChangeHandler send response IOException ", callId, ioException);
            }
        }
    }
}
