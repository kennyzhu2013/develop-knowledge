package com.cmic.bizcontroller.controllerImpl.vod.plugin;

import net.sf.json.JSONObject;

/**
 * 插件运行模式附录，用于记录转接查询对插件运行情况的一些要求
 * <p>
 * Author: luohuanfa
 * Date: 2023/2/16
 */
public class PluginModeAppendix {
    public enum ModeEnum {
        VOD_IDOL
    }

    private final ModeEnum modeEnum;
    private final JSONObject jsonResp;

    public PluginModeAppendix(ModeEnum modeEnum, JSONObject jsonResp) {
        this.modeEnum = modeEnum;
        this.jsonResp = jsonResp;
    }

    public ModeEnum getModeEnum() {
        return modeEnum;
    }


    public JSONObject getJsonResp() {
        return jsonResp;
    }

}
