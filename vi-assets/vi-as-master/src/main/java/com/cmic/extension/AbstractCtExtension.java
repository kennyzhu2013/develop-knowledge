package com.cmic.extension;

import com.cmic.BillNew;
import com.cmic.business.businessparam.ExtensionCtrlParam;
import com.cmic.dao.BizNumAuthDo;
import com.cmic.sip.controller.base.TwoLegController;
import com.cmic.sip.engine.SipEngine;

import java.util.concurrent.atomic.AtomicLong;

public abstract class AbstractCtExtension {
    private int extensionId;
    private String extensionName;
    private AtomicLong counter = new AtomicLong();

    public AbstractCtExtension(int extensionId, String extensionName) {
        this.extensionId = extensionId;
        this.extensionName = extensionName;
    }

    public abstract TwoLegController buildController(BillNew bill, BizNumAuthDo bizNumAuthDo, SipEngine sipEngine, int startMode);

    /**
     * 构建ExtensionCtrlParam,配置与extId相关的控制逻辑，默认为空null
     *
     * @return ExtensionCtrlParam
     */
    public ExtensionCtrlParam buildExtensionCtrlParam() {
        return null;
    }


}
