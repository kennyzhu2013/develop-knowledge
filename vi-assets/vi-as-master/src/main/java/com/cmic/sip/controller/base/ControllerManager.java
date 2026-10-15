package com.cmic.sip.controller.base;

import com.cmic.service.PromExporterService;
import com.cmic.service.ScheduleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;


/**
 * Controller管理类
 */
public class ControllerManager {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private static ControllerManager instance = new ControllerManager();

    public static ControllerManager getInstance() {
        return instance;
    }

    public ControllerManager()
    {
        ScheduleService.getInstance().scheduleWithFixedDelay(()->{
            try{
                PromExporterService.getInstance().refreshControllerNum(getTotalCount());
            }catch (Exception e)
            {
                logger.error("set controller num ",e);
            }
        },30,10, TimeUnit.SECONDS);

        /***清除不正常的controller**/
        ScheduleService.getInstance().scheduleWithFixedDelay(() -> {
            for (Controller controller : hashmap.values()) {
                try {
                    if (!controller.isAlive()) {
                        if (!controller.hasSendDialogTemrinate.getAndSet(true)) {
                            logger.warn("[{}] clean dead controller", controller.sessionId);
                            controller.onCleanOutEvent();
                            controller.terminated();
                        }
                        removeController(controller.sessionId);

                    }
                } catch (Exception e) {
                    logger.error("clean controller  ", e);
                }
            }

        }, 30, 60, TimeUnit.SECONDS);
    }

    public final Map<String, Controller> hashmap = new ConcurrentHashMap<>();

    public Controller getController(String sessionid) {
        return this.hashmap.getOrDefault(sessionid, null);
    }

    public void addController(String sessionid, Controller controller) {
        if (controller != null) {
            hashmap.put(sessionid, controller);
        }
    }

    public Controller removeController(String sessionid) {
        return this.hashmap.remove(sessionid);
    }

    public int getTotalCount() {
        return hashmap.size();
    }

    /**
     * 用于强制剔除通话
     */
    public void sendByToAllController() {
        for (Controller controller : hashmap.values()) {
            try {
                logger.info("sendByToSession {}",controller.sessionId);
                controller.sendByeToLegs();
            } catch (Exception e) {
                logger.error("sendByToAllController id is " + controller.sessionId, e);
            }
        }
    }


}