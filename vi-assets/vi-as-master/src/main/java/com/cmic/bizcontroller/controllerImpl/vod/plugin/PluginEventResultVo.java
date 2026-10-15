package com.cmic.bizcontroller.controllerImpl.vod.plugin;

import static com.cmic.constant.VodConstant.VOD.VOD_HTTP_CODE.OTHER_REASON;

/**
 * plugin处理event的结果
 * <p>
 * Author: luohuanfa
 * Date: 2022/11/2
 */
public class PluginEventResultVo {
    private boolean haveFoundPlugin;
    private SignalPassMode eventPassOn;
    private String code;
    private String msg;

    public enum SignalPassMode {
        NEITHER_PASS,          // 事件和信令都不传出去
        EVENT_PASS,  // 事件传给其他插件，但是信令不传，即其他插件或者BaseController不需要将该信令forward出去
        SIGNAL_PASS  // 事件和信令都传给其他插件或者BaseController,即其他插件或者BaseController需要将该信令forward出去
    }

    public static PluginEventResultVo newCommonSuccVo() {
        return new PluginEventResultVo(true, "0000", "success", SignalPassMode.NEITHER_PASS);
    }

    public static PluginEventResultVo newCustomVo(boolean haveFindPlugin, String code, String msg, SignalPassMode eventPassOn) {
        return new PluginEventResultVo(haveFindPlugin, code, msg, eventPassOn);
    }

    public static PluginEventResultVo newDealResultVo(String code, String msg) {
        return new PluginEventResultVo(true, code, msg, SignalPassMode.NEITHER_PASS);
    }

    public static PluginEventResultVo newDealSuccessVo(String code, String msg) {
        return new PluginEventResultVo(true, code, msg, SignalPassMode.NEITHER_PASS);
    }

    public static PluginEventResultVo newPluginNotFoundVo() {
        return new PluginEventResultVo(false, OTHER_REASON, "plugin not found", SignalPassMode.NEITHER_PASS);
    }

    public static PluginEventResultVo newPluginFailVo(String code, String msg) {
        return new PluginEventResultVo(true, code, msg, SignalPassMode.NEITHER_PASS);
    }

    /*public static PluginEventResultVo newPluginEventPassOn() {
        return new PluginEventResultVo(false, "", "", true);
    }*/

    public static PluginEventResultVo newPluginEventPassOn(SignalPassMode eventPassOn) {
        return new PluginEventResultVo(true, "", "", eventPassOn);
    }

    private PluginEventResultVo(boolean haveFoundPlugin, String code, String msg, SignalPassMode eventPassOn) {
        this.haveFoundPlugin = haveFoundPlugin;
        this.code = code;
        this.msg = msg;
        this.eventPassOn = eventPassOn;
    }

    public boolean isHaveFoundPlugin() {
        return haveFoundPlugin;
    }

    public void setHaveFoundPlugin(boolean haveFoundPlugin) {
        this.haveFoundPlugin = haveFoundPlugin;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setEventPassOn(SignalPassMode eventPassOn) {
        this.eventPassOn = eventPassOn;
    }


    /**
     * 是否将消息传递给下一个plugin或者外界Controller处理
     *
     * @return
     */
    public boolean isEventPassOn() {
        return eventPassOn.equals(SignalPassMode.EVENT_PASS);
    }

    /**
     * 是否将消息传递给下一个plugin或者外界Controller处理,并且信令也要 forward 出去
     *
     * @return
     */
    public boolean isSignalPassOn() {
        return eventPassOn.equals(SignalPassMode.SIGNAL_PASS);
    }

    public SignalPassMode getEventPassOn() {
        return eventPassOn;
    }

    /**
     * 是否不需要将消息、信令传递给下一个plugin或者外界Controller处理
     *
     * @return
     */
    public boolean isNeitherPassOn() {
        return eventPassOn.equals(SignalPassMode.NEITHER_PASS);
    }


    @Override
    public String toString() {
        return "PluginEventResultVo{" +
                "haveFoundPlugin=" + haveFoundPlugin +
                ", eventPassOn=" + eventPassOn +
                ", code='" + code + '\'' +
                ", msg='" + msg + '\'' +
                '}';
    }
}
