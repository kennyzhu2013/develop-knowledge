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

public class VodStopHandler implements HttpHandler {

    private static Logger logger = LoggerFactory.getLogger(VodStopHandler.class);

    @Override
    public void handle(HttpExchange exchange) {
        String billId = "";
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody()));
            StringBuffer reqBuffer = new StringBuffer();
            String s = null;
            while ((s = reader.readLine()) != null) {
                reqBuffer.append(s);
            }

            JSONObject reqJson = JSONObject.fromObject(reqBuffer.toString());
            String callId = (String) reqJson.getOrDefault("callId", "");
            logger.info("[{}] Vod stop request:{}", callId, reqJson.toString());
            billId = callId;
            String uniCode = (String) reqJson.getOrDefault("uniCode", "");
            if (StringUtils.isBlank(callId) || StringUtils.isBlank(uniCode) || StringUtils.isBlank(billId)) {
                HandlerHelper.sendResponse(exchange, new VodVo(VodConstant.VOD.VOD_HTTP_CODE.PARAM_INVALID, VodConstant.VOD.VOD_HTTP_MSG.PARAM_INVALID).toString());
                return;
            }
            NewTwoLegController controller = (NewTwoLegController) ControllerManager.getInstance().getController(billId);
            if (controller == null) {
                HandlerHelper.sendResponse(exchange, new VodVo(VodConstant.VOD.VOD_HTTP_CODE.CALL_NOT_EXIST, VodConstant.VOD.VOD_HTTP_MSG.CALL_NOT_EXIST).toString());
                return;
            }
            VodVo vodVo = PluginHttpHandler.stopVod(reqJson, controller);
            HandlerHelper.sendResponse(exchange, vodVo.toString());

        } catch (Exception e) {
            logger.error("[{}] Vod stop http request exception ", billId, e);
            try {
                HandlerHelper.sendResponse(exchange, new VodVo(VodConstant.VOD.VOD_HTTP_CODE.OTHER_REASON, VodConstant.VOD.VOD_HTTP_MSG.OTHER_REASON).toString());
            } catch (IOException ioException) {
                logger.error("[{}] Vod stop http request send response IOException ", billId, ioException);
            }
        }

    }
}
