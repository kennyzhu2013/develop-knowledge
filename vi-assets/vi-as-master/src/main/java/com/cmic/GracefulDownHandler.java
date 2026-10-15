package com.cmic;

import com.cmic.service.ScheduleService;
import com.cmic.sip.controller.base.ControllerManager;
import com.cmic.sip.engine.DtmfEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Signal;
import sun.misc.SignalHandler;

import java.util.concurrent.TimeUnit;

/**
 * 优雅停机：
 * kill -2时，触发handle接口函数，在此进行状态保存后，调用System.exit(0)结束进程；
 * 如果不在handle()中主动调用System.exit(0)，进程继续执行，不受任何影响；
 */
public class GracefulDownHandler implements SignalHandler {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private static int runCount = 0;

    @Override
    public void handle(Signal signal) {
        logger.info("handle shutdown graceful");
        // 先关闭协议栈
        DtmfEngine.getInstance().turnOnOff(false);

        // 每间隔5秒检查一次在线呼叫数，减到0时退出进程
        ScheduleService.getInstance().scheduleAtFixedRate(new Runnable() {

            @Override
            public void run() {
                // 在线呼叫数为0时，
                runCount++;
                if (ControllerManager.getInstance().getTotalCount() == 0 || runCount == 30) {
                    System.exit(0);
                }
                if (runCount == 20) {
                    logger.info(" ControllerManager sendByToAllController ing");
                    ControllerManager.getInstance().sendByToAllController();
                    logger.info(" ControllerManager sendByToAllController end");
                }
            }
        }, 5, 5, TimeUnit.SECONDS);
    }
}