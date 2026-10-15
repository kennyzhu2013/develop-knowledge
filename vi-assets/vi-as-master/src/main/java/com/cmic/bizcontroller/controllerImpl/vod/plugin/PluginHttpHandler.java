package com.cmic.bizcontroller.controllerImpl.vod.plugin;

import com.cmic.bizcontroller.controllerImpl.NewTwoLegController;
import com.cmic.bizcontroller.controllerImpl.videooutbound.ObvPluginGroup;
import com.cmic.http.vo.VodVo;
import com.cmic.util.StringUtil;
import com.cmic.constant.VodConstant;
import net.sf.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;


/**
 * 处理外部Http请求
 * <p>
 * Author: luohuanfa
 * Date: 2022/12/29
 */
public class PluginHttpHandler {
    private static Logger logger = LoggerFactory.getLogger(PluginHttpHandler.class);

    public static VodVo activateVod(JSONObject eventInfo, NewTwoLegController controller) {
        logger.info("[{}], PluginHttpCaller activateVod start", controller.getBillNew().getId());
        Map<String, Object> keyMap = new HashMap<>(eventInfo);
        keyMap.put("lastLeftRequest", controller.callRecorder.getLastLeftRequest());
        keyMap.put("lastRightResponse", controller.callRecorder.getLastRightResponse());

        keyMap.put("callerSupportWb", controller.callRecorder.getCallerSupportWb());
        keyMap.put("calleeSupportWb", controller.callRecorder.getCalleeSupportWb());
        keyMap.put("callerContactSupprtVideo", controller.callRecorder.getCallerContactSupprtVideo());
        keyMap.put("calleeContactSupportVideo", controller.callRecorder.getCalleeContactSupportVideo());

        try {
            PluginEventResultVo pluginEventResultVo = controller.getPluginManager().tackOverHttpMessage(PluginEvent.EventTypeEnum.VOD, keyMap);
            logger.info("[{}], activateVod pluginEventResultVo:{}", controller.getBillNew().getId(), pluginEventResultVo.toString());
            return new VodVo(pluginEventResultVo.getCode(), pluginEventResultVo.getMsg());
        } catch (Exception e) {
            logger.error("[{}], activateVod exception ", controller.getBillNew().getId(), e);
            return new VodVo(VodConstant.VOD.VOD_HTTP_CODE.OTHER_REASON, VodConstant.VOD.VOD_HTTP_MSG.OTHER_REASON);
        }
    }

    public static VodVo changeVodAudioCode(JSONObject eventInfo, NewTwoLegController controller) {
        String newAudioCode = eventInfo.getString("audioCode");
        logger.info("[{}], changeVodAudioCode start. newAudioCode:{}", controller.getBillNew().getId(), newAudioCode);
        controller.setVodAudioCode(newAudioCode);
        controller.controllerState.getCallInfo().setVodAudioCode(newAudioCode);
        return new VodVo(VodConstant.VOD.VOD_HTTP_CODE.SUCCESS, VodConstant.VOD.VOD_HTTP_MSG.SUCCESS);
    }

    /**
     * xmedia 返回插播结束
     *
     * @param eventInfo
     * @return
     */
    public static VodVo completeVod(JSONObject eventInfo, NewTwoLegController controller) {
        logger.info("[{}], http completeVod start", controller.getBillNew().getId());
        Map<String, Object> keyMap = new HashMap<>(eventInfo);
        PluginEventResultVo pluginEventResultVo = null;
        String code = (String) eventInfo.get("code");
        if (StringUtil.isBlankOrNull(code)) {
            keyMap.put("code", "0000");
        }
        try {
            PluginManager pluginManager = controller.getPluginManager();
            if (pluginManager != null) {
                pluginEventResultVo = pluginManager.tackOverHttpMessage(PluginEvent.EventTypeEnum.VODCOMPLETE, keyMap);
                return new VodVo(pluginEventResultVo.getCode(), pluginEventResultVo.getMsg());
            } else {
                logger.error("[{}], completeVod pluginManager is null ", controller.getBillNew().getId());
                return new VodVo(VodConstant.VOD.VOD_HTTP_CODE.OTHER_REASON, VodConstant.VOD.VOD_HTTP_MSG.OTHER_REASON);
            }

        } catch (Exception e) {
            logger.error("[{}], completeVod exception ", controller.getBillNew().getId(), e);
            return new VodVo(VodConstant.VOD.VOD_HTTP_CODE.OTHER_REASON, VodConstant.VOD.VOD_HTTP_MSG.OTHER_REASON);
        }
    }

    /**
     * app 停止插播
     *
     * @param eventInfo
     * @return
     */
    public static VodVo stopVod(JSONObject eventInfo, NewTwoLegController controller) {
        logger.info("[{}], stopVod start", controller.getBillNew().getId());
        try {
            PluginEventResultVo pluginEventResultVo = controller.getPluginManager().tackOverHttpMessage(PluginEvent.EventTypeEnum.VODRE, eventInfo);
            return new VodVo(pluginEventResultVo.getCode(), pluginEventResultVo.getMsg());
        } catch (Exception e) {
            logger.error("[{}], stopVod exception ", controller.getBillNew().getId(), e);
            return new VodVo(VodConstant.VOD.VOD_HTTP_CODE.OTHER_REASON, VodConstant.VOD.VOD_HTTP_MSG.OTHER_REASON);
        }
    }

    /**
     * 视频播放控制
     *
     * @return
     */
    public static VodVo vodControl(int opType, int opData, NewTwoLegController controller) {
        logger.info("[{}], vodControl start", controller.getBillNew().getId());
        PluginEventResultVo resultVo;
        try {
            PluginManager pluginManager = controller.getPluginManager();
            AbstractPlugin currentPlugin = pluginManager.getCurrentPlugin();
            if (!(currentPlugin instanceof VodPluginGroup || currentPlugin instanceof ObvPluginGroup)) {
                logger.error("[{}], vodControl but not in vod process", controller.getBillNew().getId());
                resultVo = PluginEventResultVo.newPluginFailVo(VodConstant.VOD.VOD_HTTP_CODE.OTHER_REASON, "not in vod process!");
            } else {
                controller.getMediaSession().vodControl(opType, opData);
                resultVo = PluginEventResultVo.newCommonSuccVo();
            }
            return new VodVo(resultVo.getCode(), resultVo.getMsg());
        } catch (Exception e) {
            logger.error("[{}], vodControl exception ", controller.getBillNew().getId(), e);
            resultVo = PluginEventResultVo.newDealResultVo(VodConstant.VOD.VOD_HTTP_CODE.OTHER_REASON, VodConstant.VOD.VOD_HTTP_MSG.OTHER_REASON);
            return new VodVo(resultVo.getCode(), resultVo.getMsg());
        }
    }


}
