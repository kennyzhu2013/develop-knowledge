package com.cmic.extension;

import com.cmic.BillNew;
import com.cmic.bizcontroller.controllerImpl.B2BWithBillController;
import com.cmic.bizcontroller.controllerImpl.B2BWithoutBillController;
import com.cmic.business.BusinessEvent;
import com.cmic.business.StandardEvent;
import com.cmic.business.businessparam.ExtensionCtrlParam;
import com.cmic.bizcontroller.ControllerAgent;
import com.cmic.bizcontroller.controllerImpl.BaseController;
import com.cmic.bizcontroller.controllerImpl.ErrorController;
import com.cmic.dao.BizNumAuthDo;
import com.cmic.pojo.Optype;
import com.cmic.sip.controller.base.TwoLegController;
import com.cmic.sip.engine.SipEngine;
import com.cmic.util.ControllerSelectorBuilder;

import java.util.HashMap;

public class StandardExtension extends AbstractCtExtension {

    public StandardExtension(int extId, String name) {
        super(extId, name);
    }

    @Override
    public TwoLegController buildController(BillNew billnew, BizNumAuthDo bizNumAuthDo, SipEngine sipEngine, int startMode) {
        BusinessEvent businessEvent = new StandardEvent(billnew, bizNumAuthDo, buildExtensionCtrlParam());
        HashMap<Optype, String> controllerSelector;
        if (startMode == 1) {
            controllerSelector = new ControllerSelectorBuilder()
                    .optype_0(ErrorController.class.getName())
                    .optype_1(B2BWithBillController.class.getName())
                    .buildControllerSelector();
        } else {
            controllerSelector = new ControllerSelectorBuilder()
                    .optype_0(ErrorController.class.getName())
                    .optype_1(BaseController.class.getName())
                    .buildControllerSelector();
        }
        return new ControllerAgent(billnew, bizNumAuthDo, businessEvent, sipEngine, controllerSelector, startMode);
    }

    @Override
    public ExtensionCtrlParam buildExtensionCtrlParam() {
        ExtensionCtrlParam extensionCtrlParam = new ExtensionCtrlParam();
        extensionCtrlParam.setOrigCallerNum4InternationalIntercept(true);
        return extensionCtrlParam;
    }

}
