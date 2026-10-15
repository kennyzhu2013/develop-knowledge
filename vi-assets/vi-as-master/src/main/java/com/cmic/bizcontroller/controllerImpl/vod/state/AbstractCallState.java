package com.cmic.bizcontroller.controllerImpl.vod.state;

import com.cmic.bizcontroller.controllerImpl.vod.SipMessageDesc;
import com.cmic.bizcontroller.controllerImpl.vod.plugin.Plugin;

import java.util.List;

/**
 * 状态的抽象类
 * <p>
 * Author: luohuanfa
 * Date: 2022/10/9
 */
public abstract class AbstractCallState {
    protected boolean isUac;
    public List<Plugin> pluginList;
    private boolean isThisMessageThatChangeTheState;
    protected int stateIndex;

    public AbstractCallState(boolean isUac, int stateIndex) { // TODO: 2023/1/3 这里需要删一下，因为不是static的
        this.isUac = isUac;
        isThisMessageThatChangeTheState = true;
        this.stateIndex = stateIndex;
    }

    /**
     * 根据外部请求推动当前状态机变更
     *
     * @param sipMessageDesc 当前信令
     * @return 返回自身或者下一节点
     */
    protected abstract AbstractCallState stateStepOn(SipMessageDesc sipMessageDesc);

    public AbstractCallState stateStepOnNext(SipMessageDesc sipMessageDesc) {
        if (isThisMessageThatChangeTheState) {
            isThisMessageThatChangeTheState = false;
        }
        return stateStepOn(sipMessageDesc);
    }

    ;

    public boolean isThisMessageThatChangeTheState() {
        return isThisMessageThatChangeTheState;
    }

    public int getStateIndex() {
        return stateIndex;
    }
}
