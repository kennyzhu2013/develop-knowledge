package com.cmic.bizcontroller.controllerImpl.vod.plugin;

// import com.cmic.bizcontroller.controllerImpl.videooutbound.ObvPluginKeeper;
import com.cmic.bizcontroller.controllerImpl.vod.ControllerState;
import com.cmic.bizcontroller.controllerImpl.vod.SipMessageDesc;
import com.cmic.bizcontroller.controllerImpl.vod.plugin.earlyvideo.VideoRBTKeeper;
import com.cmic.bizcontroller.controllerImpl.vod.state.AbstractCallState;
import com.cmic.bizcontroller.controllerImpl.vod.state.EarlyState;
import com.cmic.bizcontroller.controllerImpl.vod.state.PickUpState;
import com.cmic.bizcontroller.controllerImpl.vod.state.RingedState;
import com.cmic.sip.event.SipRequestEvent;
import com.cmic.sip.event.SipResponseEvent;
import com.cmic.sip.leg.Leg;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Sets;
import gov.nist.javax.sip.message.SIPRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Author: luohuanfa
 * Date: 2022/9/28
 */
public class PluginManager {
    
    private static final Logger logger = LoggerFactory.getLogger(PluginManager.class);
    private final LinkedHashMultimap<Class<?>, PluginKeeper> pluginStateFilterMap = LinkedHashMultimap.create();
    private final LinkedHashMultimap<PluginEvent.EventTypeEnum, PluginKeeper> eventTypeEnumMap = LinkedHashMultimap.create();
    private ControllerState state;
    private String sessionId;

    public PluginManager(ControllerState state) {
        this.state = state;
        this.sessionId = state.getCallInfo().getBillNew().getId();
    }

    private AbstractPlugin currentPlugin;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

    public void registerPlugin(PluginKeeperSelectorEnum pluginKeeperSelectorEnum) {
        // logger.info("[{}],[PluginManager-registerPlugin]: {}", getBillId(), pluginKeeperSelectorEnum.name());
        for (Class<? extends AbstractCallState> callState : pluginKeeperSelectorEnum.getCallStates()) {
            for (PluginEvent.EventTypeEnum eventTypeEnum : pluginKeeperSelectorEnum.getEventType()) {
                registerPlugin(callState, eventTypeEnum, pluginKeeperSelectorEnum.getKeeperPlugin());
            }
        }
    }

    /**
     * 将plugin绑定到对应的状态机
     *
     * @param state         插件监听的通话状态
     * @param eventTypeEnum 插件监听的事件类型
     * @param pluginKeeper  插件Keeper
     */
    public void registerPlugin(Class<? extends AbstractCallState> state, PluginEvent.EventTypeEnum eventTypeEnum, PluginKeeper pluginKeeper) {
        pluginStateFilterMap.put(state, pluginKeeper);
        eventTypeEnumMap.put(eventTypeEnum, pluginKeeper);
    }


    public boolean anyPluginTakeIt(Leg leg, PluginEvent event) throws Exception {
        if (currentPlugin != null) {
            return true;
        }
        // 基于当前腿的通话状态和当前事件类型获取监听的PluginKeeper
        Collection<PluginKeeper> currentPluginsKeeper = getCurrentPluginsKeeper(leg, event.getType());
        if (currentPluginsKeeper == null || currentPluginsKeeper.size() == 0) {
            return false;
        }
        // 将当前事件类型和当前腿的通话状态逐一交给前面的PluginKeeper判断是否需要处理，如果有则取第一个。此处不支持多个PluginKeeper串联？
        Optional<PluginKeeper> first = currentPluginsKeeper.stream().filter(plugin -> plugin.shouldTake(event, state)).findFirst();
        if (first.isPresent()) {
            currentPlugin = (AbstractPlugin) first.get().createPlugin(event, state);
            // 如果有插件接管了此消息，那么清除这个消息对CommonRequestState的影响
            // 1. 由于插件工作的前提是CommonRequestState完全，所以进入此create之前CommonRequestState必为空
            // 2. 由于插件接管了此消息，那么如果CommonRequestState不为空，一定是此消息带来的
            // 所以直接clear是安全的
            state.clearCommonRequestState();
            return true;
        }
        return false;
    }

    /**
     * 获取符合当前状态的PluginKeeper
     *
     * @param leg           消息来自哪条腿
     * @param eventTypeEnum 事件类型
     * @return 符合当前状态的所有PluginKeeper
     */
    private Collection<PluginKeeper> getCurrentPluginsKeeper(Leg leg, PluginEvent.EventTypeEnum eventTypeEnum) {
        AbstractCallState currentState = state.getCurrentState(leg);
        if (currentState == null) {
            logger.error("[{}], [PluginManager-getCurrentPlugins], currentState is null", getBillId());
            return null;
        }
        Set<PluginKeeper> pluginKeepers = pluginStateFilterMap.get(currentState.getClass());
        Set<PluginKeeper> pluginKeepers2 = eventTypeEnumMap.get(eventTypeEnum);
        return Sets.intersection(pluginKeepers, pluginKeepers2);
    }

    /**
     * 使用当前Plugin执行此event，默认是第一个event
     *
     * @param leg   消息来自哪条腿
     * @param event 消息
     * @return 处理结果
     */
    private PluginEventResultVo executePlugin(Leg leg, PluginEvent event) throws Exception {
        return executePlugin(leg, event, true);
    }

    /**
     * 使用当前Plugin执行此event
     *
     * @param leg                消息来自哪条腿
     * @param event              消息
     * @param isPluginFirstEvent 是否是当前CurrentPlugin处理的第一个Event(因为第一个Event需要执行accept()而不是request/response)
     * @return 处理结果
     */
    private PluginEventResultVo executePlugin(Leg leg, PluginEvent event, boolean isPluginFirstEvent) throws Exception {
        if (event.getType().equals(PluginEvent.EventTypeEnum.SIP_RESPONSE) || event.getType().equals(PluginEvent.EventTypeEnum.SIP_REQUEST)) {
            logger.info("[{}], plugin which should take the message:{} found! Is left leg {}", sessionId, event.getSipMessageDesc(), leg.isLeft());
        } else {
            logger.info("[{}], plugin which should take the message:{} found! Is left leg {}", sessionId, event.getType(), leg.isLeft());
        }
        PluginEventResultVo resultVo;
        if (isPluginFirstEvent) {
            resultVo = currentPlugin.accept(event);
        } else {
            switch (event.getType()) {
                case SIP_REQUEST:
                    resultVo = currentPlugin.doRequest(leg, event.getRequestEvent());
                    break;
                case SIP_RESPONSE:
                    resultVo = currentPlugin.doResponse(leg, event.getResponseEvent());
                    break;
                default:
                    resultVo = currentPlugin.accept(event);
                    break;
            }
        }
        logger.info("[{}] Plugin Manager executePlugin resultVo:{}", getBillId(), resultVo);
        doWhenPluginComplete();
        return resultVo;
    }

    private void doWhenPluginComplete() {
        if (currentPlugin != null && currentPlugin.isComplete()) {
            logger.info("[{}], PluginManager doWhenPluginComplete current plugin:{} complete", sessionId, currentPlugin);
            try {
                currentPlugin.processPluginTermination();
            } catch (Exception e) {
                logger.error("[{}] PluginManager processPluginTermination exception", sessionId, e);
            }
            currentPlugin = null;
        }
    }

    private String getBillId() {
        return sessionId;
    }

    public void businessAwait(int waitMills) throws InterruptedException {
        condition.await(waitMills, TimeUnit.MILLISECONDS);
    }

    /**
     * 处理外界Controller进来的Request
     *
     * @param leg          消息来源腿
     * @param requestEvent 请求
     * @param desc         消息描述
     * @return 处理结果
     * @throws Exception 异常
     */
    public PluginEventResultVo tackOverSipRequest(Leg leg, SipRequestEvent requestEvent, SipMessageDesc desc) throws Exception {
        PluginEvent event = PluginEvent.getSipRequestEventInstance(leg, requestEvent, desc);
        return tackOverSipEvent(leg, event, desc, requestEvent.getRequest().getTransactionId());
    }


    /**
     * 处理外界Controller进来的SipResponse
     *
     * @param leg           消息来源腿
     * @param responseEvent 效应消息
     * @param desc          消息描述
     * @return 处理结果
     * @throws Exception 异常
     */
    public PluginEventResultVo tackOverSipResponse(Leg leg, SipResponseEvent responseEvent, SipMessageDesc desc) throws Exception {
        PluginEvent event = PluginEvent.getSipResponseEventInstance(leg, responseEvent, desc);
        return tackOverSipEvent(leg, event, desc, responseEvent.getResponse().getTransactionId());
    }


    /**
     * 处理Sip消息
     *
     * @param leg           消息来源腿
     * @param event         事件
     * @param desc          消息描述
     * @param transactionId 事务Id
     * @return 处理结果
     * @throws Exception 异常
     */
    private PluginEventResultVo tackOverSipEvent(Leg leg, PluginEvent event, SipMessageDesc desc, String transactionId) throws Exception {
        // 加入锁以兼容主叫 ACK 和 reinvite 几乎同时到达的情况
        lock.lock();
        try {
            // 在可能将事件交付给插件处理前，判断pluginManager是否需要主动记录这条消息
            boolean eventShouldBeRecorded = shouldEventBeRecordByMachine(desc);
            PluginEventResultVo pluginEventResultVo = PluginEventResultVo.newPluginNotFoundVo();
            // currentPlugin不为空，则说明当前有正在活跃的插件，直接当前的插件接管本次的消息
            if (currentPlugin != null) {
                // 执行CurrentPlugin逻辑操作，如果返回结果没有将消息传递出去则直接返回true，即接管消息
                pluginEventResultVo = executePlugin(leg, event, false);
                logger.info("[{}], [PluginManager-tackOverSipEvent],currentPlugin:{} pluginEventResultVo:{}", getBillId(), currentPlugin, pluginEventResultVo);
                if (pluginEventResultVo.isNeitherPassOn()) {
                    if (eventShouldBeRecorded) {
                        state.consumeSipMessage(leg, desc, transactionId);
                    }
                    return pluginEventResultVo;
                }
            }
            // 如果currentPlugin为空或者将消息传递出来给别的plugin处理，则进入下面的逻辑，即需要给别的Plugin看是否消费
            if (!anyPluginTakeIt(leg, event)) {
                logger.debug("[{}], [PluginManager-tackOverSipEvent], no plugin take the sip message", getBillId());
                state.consumeSipMessage(leg, desc, transactionId);
                return pluginEventResultVo;
            }
            pluginEventResultVo = executePlugin(leg, event, true);
            logger.info("[{}], [PluginManager-tackOverSipEvent], find plugin ready, currentPlugin:{},pluginEventResultVo:{}", getBillId(), currentPlugin, pluginEventResultVo);
            //boolean haveTake = pluginEventResultVo.isHaveFoundPlugin() && !pluginEventResultVo.isEventPassOn();
            boolean haveTake = pluginEventResultVo.isHaveFoundPlugin() && pluginEventResultVo.isNeitherPassOn();
            // 有三种情况需要推动状态机的状态变更的
            // 1. 前面判断到应该消费的 2. 没有被插件接管的消息 3. 接管了但是还是传递给了外界的
            if (eventShouldBeRecorded || !haveTake || !pluginEventResultVo.isNeitherPassOn()) {
                state.consumeSipMessage(leg, desc, transactionId);
            }
            return pluginEventResultVo;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 判断是否应该被状态机记录状态
     *
     * @param desc sip消息描述
     * @return 是否应该被状态机记录状态
     */
    private boolean shouldEventBeRecordByMachine(SipMessageDesc desc) {
        boolean isResponse = desc.getRequestOrResponse().equals(SipMessageDesc.RequestOrResponse.RESPONSE);
        boolean isNotPickUp = state.getStateMap().get(state.getLeftLeg()).getStateIndex() < PickUpState.PickUpStateCode;

        // 属于初始的Invite
        if ((desc.getMethod().equals(SIPRequest.INVITE) || desc.getMethod().equals(SIPRequest.ACK)) && isNotPickUp) {
            return true;
        }
        // 如果currentPlugin为空并且消息是响应或者是ACK，说明前面的请求是外界处理的，所以应该消费这个消息
        if (currentPlugin == null) {
            return isResponse || desc.getMethod().equals(SIPRequest.ACK);
        }
        return false;
    }

    /**
     * 处理所有的http请求
     *
     * @param eventTypeEnum 事件
     * @param httpData      http请求体
     * @return 处理结果
     * @throws Exception 异常
     */
    public PluginEventResultVo tackOverHttpMessage(PluginEvent.EventTypeEnum eventTypeEnum, Map<String, Object> httpData) throws Exception {
        logger.info("[{}],tackOverHttpMessage start", getBillId());
        PluginEvent httpEvent = PluginEvent.getHttpEventInstance(eventTypeEnum, httpData);
        if (anyPluginTakeIt(state.getLeftLeg(), httpEvent)) {
            logger.info("[{}], [PluginManager-tackOverHttpMessage], find plugin :{} ready", getBillId(), currentPlugin.getClass().getName());
            return executePlugin(state.getLeftLeg(), httpEvent);
        } else {
            logger.info("[{}], [PluginManager-tackOverHttpMessage], find but no plugin ready", getBillId());
            return PluginEventResultVo.newPluginNotFoundVo();
        }
    }

    public enum PluginKeeperSelectorEnum {
        VOD {
            @Override
            public List<Class<? extends AbstractCallState>> getCallStates() {
                return vodStateList;
            }

            @Override
            public List<PluginEvent.EventTypeEnum> getEventType() {
                return vodEventTypeList;
            }

            @Override
            public PluginKeeper getKeeperPlugin() {
                return new VodPluginGroupKeeper();
            }
        },

        PickUpVOD {
            @Override
            public List<Class<? extends AbstractCallState>> getCallStates() {
                return pickUpVodStateList;
            }

            @Override
            public List<PluginEvent.EventTypeEnum> getEventType() {
                return pickUpVodEventTypeList;
            }

            @Override
            public PluginKeeper getKeeperPlugin() {
                return new VodPluginGroupKeeper();
            }
        },

        // VideoOutBound {
        //     @Override
        //     public List<Class<? extends AbstractCallState>> getCallStates() {
        //         return videoOutBoundStateList;
        //     }
        //
        //     @Override
        //     public List<PluginEvent.EventTypeEnum> getEventType() {
        //         return videoOutBoundEventTypeList;
        //     }
        //
        //     @Override
        //     public PluginKeeper getKeeperPlugin() {
        //         return new ObvPluginKeeper();
        //     }
        // },
        VRBT {
            @Override
            public List<Class<? extends AbstractCallState>> getCallStates() {
                return vrbtStateList;
            }

            @Override
            public List<PluginEvent.EventTypeEnum> getEventType() {
                return vrbtEventTypeList;
            }

            @Override
            public PluginKeeper getKeeperPlugin() {
                return new VideoRBTKeeper();
            }
        };
        // SECRETARY {
        //     @Override
        //     public List<Class<? extends AbstractCallState>> getCallStates() {
        //         return xSecretaryStateList;
        //     }
        //
        //     @Override
        //     public List<PluginEvent.EventTypeEnum> getEventType() {
        //         return xSecretaryEventTypeList;
        //     }
        //
        //     @Override
        //     public PluginKeeper getKeeperPlugin() {
        //         return xSecretaryKeeper;
        //     }
        // };
        private static final List<Class<? extends AbstractCallState>> vodStateList = new ArrayList<>(2);
        private static final List<Class<? extends AbstractCallState>> pickUpVodStateList = new ArrayList<>(2);
        private static final List<Class<? extends AbstractCallState>> videoOutBoundStateList = new ArrayList<>(2);
        private static final List<Class<? extends AbstractCallState>> vrbtStateList = new ArrayList<>(2);
        private static final List<Class<? extends AbstractCallState>> xSecretaryStateList = new ArrayList<>(2);
        private static final List<PluginEvent.EventTypeEnum> vodEventTypeList = new ArrayList<>(2);
        private static final List<PluginEvent.EventTypeEnum> pickUpVodEventTypeList = new ArrayList<>(2);
        private static final List<PluginEvent.EventTypeEnum> videoOutBoundEventTypeList = new ArrayList<>(2);
        private static final List<PluginEvent.EventTypeEnum> vrbtEventTypeList = new ArrayList<>(2);
        private static final List<PluginEvent.EventTypeEnum> xSecretaryEventTypeList = new ArrayList<>(2);
        // private static final XSecretaryKeeper xSecretaryKeeper = new XSecretaryKeeper();

        static {
            vodStateList.add(PickUpState.class);
            pickUpVodStateList.add(EarlyState.class);
            pickUpVodStateList.add(RingedState.class);
            videoOutBoundStateList.add(EarlyState.class);
            videoOutBoundStateList.add(RingedState.class);
            vrbtStateList.add(EarlyState.class);
            xSecretaryStateList.add(EarlyState.class);

            vodEventTypeList.add(PluginEvent.EventTypeEnum.VOD);
            vodEventTypeList.add(PluginEvent.EventTypeEnum.VODRE);
            vodEventTypeList.add(PluginEvent.EventTypeEnum.VODCOMPLETE);
            pickUpVodEventTypeList.add(PluginEvent.EventTypeEnum.SIP_REQUEST);
            videoOutBoundEventTypeList.add(PluginEvent.EventTypeEnum.SIP_REQUEST);
            vrbtEventTypeList.add(PluginEvent.EventTypeEnum.SIP_RESPONSE);

            xSecretaryEventTypeList.add(PluginEvent.EventTypeEnum.SIP_RESPONSE);
            xSecretaryEventTypeList.add(PluginEvent.EventTypeEnum.SIP_REQUEST);
        }

        PluginKeeperSelectorEnum() {
        }

        public abstract List<Class<? extends AbstractCallState>> getCallStates();

        public abstract List<PluginEvent.EventTypeEnum> getEventType();

        public abstract PluginKeeper getKeeperPlugin();

    }

    public AbstractPlugin getCurrentPlugin() {
        return currentPlugin;
    }
}
