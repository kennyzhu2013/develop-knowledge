package com.cmic.bizcontroller;

import com.cmic.BillNew;
import com.cmic.business.BusinessEvent;
import com.cmic.bizcontroller.controllerImpl.NewTwoLegController;
import com.cmic.business.businessparam.CallParameter;
import com.cmic.dao.BizNumAuthDo;
import com.cmic.sip.engine.SipEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ControllerFactory {
    public static Logger logger = LoggerFactory.getLogger(ControllerFactory.class);

    public static NewTwoLegController getController(String name, BillNew billNew, BizNumAuthDo bizNumAuthDo, BusinessEvent businessEvent, CallParameter callParameter, SipEngine engine) {
        NewTwoLegController instance = null;
        try {
            instance = (NewTwoLegController) Class.forName(name).getDeclaredConstructor(BillNew.class, BizNumAuthDo.class, BusinessEvent.class, CallParameter.class, SipEngine.class).newInstance(billNew, bizNumAuthDo, businessEvent, callParameter, engine);
        } catch (Exception e) {
            logger.error("bill id is {}, build controller error {}", billNew.getId(), e);
        }
        return instance;

    }

}
