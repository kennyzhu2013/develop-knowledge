package com.cmic.bizcontroller.controllerImpl.vod.plugin;

import com.cmic.bizcontroller.controllerImpl.vod.ControllerState;

public interface PluginFactory {
    /**
     * 创建对应的plugin
     *
     * @param event event本体
     * @param state 当前通话状态
     * @return plugin实例
     */
    Plugin createPlugin(PluginEvent event, ControllerState state) throws Exception;
}
