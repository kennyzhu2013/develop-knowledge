package com.cmic.bizcontroller;

import com.cmic.BillNew;
import com.cmic.business.BusinessEvent;
import com.cmic.business.ViConf;
import com.cmic.business.businessparam.CallParameter;
import com.cmic.bizcontroller.controllerImpl.*;
import com.cmic.constant.CommonConstant;
import com.cmic.dao.BizNumAuthDo;
import com.cmic.pojo.BillAppendReasonEnum;
import com.cmic.pojo.Optype;
import com.cmic.service.*;
import com.cmic.sip.controller.base.ControllerManager;
import com.cmic.sip.controller.base.TwoLegController;
import com.cmic.sip.engine.SipEngine;
import com.cmic.state.FinishState;
import com.cmic.util.*;
import gov.nist.javax.sip.message.SIPRequest;
import lombok.Getter;
import org.apache.commons.lang.StringUtils;

import java.util.*;

public class ControllerAgent extends TwoLegController {
    BillNew billNew;
    BusinessEvent businessEvent;
    BizNumAuthDo bizNumAuthDo;
    // 真正实现功能类，有两种模式，正常接续模式或拒绝
    NewTwoLegController controller;
    @Getter
    CallParameter callParameter;
    // controller选择器（选择对应的流程的controller）目前语音质检流程：接续、拒绝
    private final HashMap<Optype, String> controllerSelector;
    int startMode = 0;

    public ControllerAgent(BillNew billNew, BizNumAuthDo bizNumAuthDo, BusinessEvent businessEvent, SipEngine engine, HashMap<Optype, String> controllerSelector, int startMode) {
        super(billNew.getId(), engine);
        isNeedCacheSingallingLog = false;
        this.billNew = billNew;
        this.businessEvent = businessEvent;
        this.bizNumAuthDo = bizNumAuthDo;
        this.controllerSelector = controllerSelector;
        this.startMode = startMode;
    }

    @Override
    public void start(SIPRequest sipRequest) throws Exception {
        XmodePromExporterService.getInstance().incInCallNum();
        ViConf viConf = getViConf(bizNumAuthDo);
        businessEvent.setViConf(viConf);

        String historyInfoIndex1PhoneNum = HistoryInfoParserUtil.getHistoryInfoIndex1PhoneNum(sipRequest);
        if (StringUtils.isNotBlank(historyInfoIndex1PhoneNum)) {
            DtmfLogger.info(sessionId, "history-info-invite, index1:{}", historyInfoIndex1PhoneNum);
        }
        // callParameter 必须在 submitDbInsert 之前，不然 dbIndex 会无法更新
        callParameter = businessEvent.buildDefaultCallParameter();
        businessEvent.processCallParameter(callParameter);
        businessEvent.fillBillNew(callParameter);
        DbAsyncService.getInstance().submitDbInsert(this, billNew, bizNumAuthDo, callParameter);
        // 企业账户可用状态仅受终端配置控制
        if (!viConf.isBizStateActive()) {
            controller = ControllerFactory.getController(controllerSelector.get(Optype.CloseOptype), this.billNew, this.bizNumAuthDo, this.businessEvent, callParameter, this.engine);
            DtmfLogger.info(sessionId, "company or customer is not alive or no need call");
            ControllerManager.getInstance().addController(sessionId, controller);
            callParameter.setDoParameterFinishState(FinishState.OTHER);
            callParameter.setDoParameterFinishStateErrorMsg("company or customer not alive");
            billNew.setAppendReason(BillAppendReasonEnum.COMPANY_OR_CUSTOMER_INACTIVE_15.getCode());
            controller.start(sipRequest);
            return;
        }
        // 启动模式不是普通业务模式的时候，不执行策略查询
        if(this.startMode != 0){
            startController(sipRequest, controller);
            return;
        }
        businessEvent.doStrategyQuery(callParameter, resp -> {
            try {
                if (callParameter.getOpType() == CommonConstant.OPTYPE.DISCONNECT) {
                    controller = ControllerFactory.getController(controllerSelector.get(Optype.CloseOptype), this.billNew, this.bizNumAuthDo, this.businessEvent, callParameter, this.engine);
                    ControllerManager.getInstance().addController(sessionId, controller);
                }
                // 目前不拦截，但是可能会有appendReason
                billNew.setAppendReason(callParameter.getOptypeAppendReason());
            } catch(Exception e) {
                DtmfLogger.error(sessionId, "process strategyQuery  Error", e);
            }
            startController(sipRequest, controller);
        });
    }

    private void startController(SIPRequest sipRequest, NewTwoLegController controller) {
        try {

            if (controller == null) {
                controller = ControllerFactory.getController(controllerSelector.get(Optype.optypeOf(callParameter.getOpType())), this.billNew, this.bizNumAuthDo, this.businessEvent, callParameter, this.engine);
            }
            DtmfLogger.info(sessionId, "ready controller:{}, opType:{}, event:{}", controller.getClass().getName(), callParameter.getOpType(), businessEvent.getEventName());
            DtmfLogger.info(sessionId, "callParameter的参数：{}", callParameter.toString());
            controller.registerPlugins();
            ControllerManager.getInstance().addController(sessionId, controller);
            controller.signalParam.update(billNew.getId(), bizNumAuthDo.getCompanyId(), String.valueOf(bizNumAuthDo.getCustomerId()));
            if(controller.infoCollector != null){
                controller.infoCollector.setNeedMidOrigMsgHeader(callParameter.isSkipIdentityVerify4MidNum());
            }
            controller.start(sipRequest);
        } catch (Exception e) {
            DtmfLogger.error(sessionId, "startController error", e);
        }
    }


    private ViConf getViConf(BizNumAuthDo bizNumAuthDo) {
        return new ViConf(bizNumAuthDo);
    }

    @Override
    public void terminated() throws Exception {

    }

}
