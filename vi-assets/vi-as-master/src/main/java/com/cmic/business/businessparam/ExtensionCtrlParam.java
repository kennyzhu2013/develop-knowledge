package com.cmic.business.businessparam;

/**
 * 配置与extId相关的控制逻辑，具体控制逻辑通过{@link com.cmic.extension.AbstractCtExtension#buildExtensionCtrlParam}实现
 * 可以通过具体extension的{@link com.cmic.extension.AbstractCtExtension#buildExtensionCtrlParam}方法来实现
 *
 * @author XH
 * @date 2024/4/19 10:51
 */
public class ExtensionCtrlParam {

    /**
     * 国际拦截时，话单中是否使用原始的主叫号码
     */
    private boolean origCallerNum4InternationalIntercept;

    public ExtensionCtrlParam() {
    }

    public boolean isOrigCallerNum4InternationalIntercept() {
        return origCallerNum4InternationalIntercept;
    }

    public void setOrigCallerNum4InternationalIntercept(boolean origCallerNum4InternationalIntercept) {
        this.origCallerNum4InternationalIntercept = origCallerNum4InternationalIntercept;
    }
}
