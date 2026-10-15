package com.cmic.bizcontroller.controllerImpl.vod.plugin;


import com.cmic.bizcontroller.controllerImpl.vod.ControllerState;

/**
 * 消息进入plugin的接待者，过滤非当前plugin处理的消息
 *
 * @author COLINAL
 */
public interface PluginFilter {
    /**
     * @return true： plugin应该处理当前消息
     * 判断是否应该处理当前消息
     */
    boolean shouldTake(PluginEvent event, ControllerState state);

}
