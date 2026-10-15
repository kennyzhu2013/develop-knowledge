package com.cmic.service;

import com.cmic.config.metrics.CommonMetrics;
import com.cmic.pojo.bo.TaskMqBo;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import com.cmic.service.task.SubTaskService;
import com.cmic.service.task.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
 @ConditionalOnProperty(name = "masterServer", havingValue = "true", matchIfMissing = false)
public class TaskCallbackSchedulerService {
     @Autowired
     private TaskService taskService;
     @Autowired
     private SubTaskService subTaskService;
     @Autowired
     private MqService mqService;
     @Autowired
     private CommonMetrics commonMetrics;

     /***
      *   1、统计未回调数量：实时（Task创建时间大于10min）、离线（6h、12h、24h）
      *   2、超时回调：实时（1h）、离线（0-2优先级48h、默认24h）
      *
      */
     @Scheduled(fixedRate = 60*60*1000)
     public void pollTasks() {
         statTasks();
         callbackTasks();
     }

    public void statTasks() {
        try {
            log.info("stat no callback task start...");
            // 统计实时10m未回调
            Map<String, Integer> countMap = taskService.countStreamTaskByProcessId(LocalDateTime.now().minusMinutes(10));
            int sum10MinutesCount = 0;
            for (Map.Entry<String, Integer> entry : countMap.entrySet()) {
                String processId = entry.getKey();
                Integer count = entry.getValue();
                sum10MinutesCount += count;
                commonMetrics.taskTimeout10MinutesInc(processId, count);
            }
            log.info("stat stream task no callback - 10 min count:{}", sum10MinutesCount);

            // 统计离线6h、12h、24h未回调
            LocalDateTime now = LocalDateTime.now();
            List<Task> timeout6HoursTasks = taskService.getTimeoutTaskByTime(false, now.minusHours(6));
            int sum6HoursCount = 0, sum12HoursCount = 0, sum24HoursCount = 0;
            for (Task task : timeout6HoursTasks) {
                int notCallbackCount = subTaskService.countByNotCallbackAndTaskId(task.getId());
                LocalDateTime taskCreateTime = task.getCreateTime();
                if (Duration.between(taskCreateTime, now).toHours() >= 6) {
                    sum6HoursCount += notCallbackCount;
                    commonMetrics.taskTimeout6HoursInc(task.getProcessId(), notCallbackCount);
                }
                if (Duration.between(taskCreateTime, now).toHours() >= 12) {
                    sum12HoursCount += notCallbackCount;
                    commonMetrics.taskTimeout12HoursInc(task.getProcessId(), notCallbackCount);
                }
                if (Duration.between(taskCreateTime, now).toHours() >= 24) {
                    sum24HoursCount += notCallbackCount;
                    commonMetrics.taskTimeout24HoursInc(task.getProcessId(), notCallbackCount);
                }
            }
            log.info("stat batch task no callback - 6h count:{}, 12h count:{}, 24h count:{}", sum6HoursCount, sum12HoursCount, sum24HoursCount);
        } catch (Exception e) {
            log.error("stat no callback task error", e);
        }
    }

    public void callbackTasks() {
        log.info("task timeout callback start...");
        LocalDateTime now = LocalDateTime.now();
        List<Task> batchTasks = taskService.getTimeoutTaskByTime(now.minusHours(24));
        List<Task> filteredBatchTasks = batchTasks.stream()
                .filter(this::shouldBatchTaskCallback)
                .collect(Collectors.toList());
        log.info("task timeout callback size:{}", filteredBatchTasks.size());

        doCallbackTask(filteredBatchTasks);
        log.info("task timeout callback finish");
    }

    /**
     * 判断批处理任务是否需要回调
     * 规则：优先级大于配置阈值(如2) 或 创建时间超过配置时长(如48小时)
     */
    private boolean shouldBatchTaskCallback(Task task) {
        long diffHours = Duration.between(task.getCreateTime(), LocalDateTime.now()).toHours();
        return task.getPriority() > 2 || diffHours >= 48;
    }

    public void doCallbackTask(List<Task> taskList) {
        for (Task task : taskList) {
            try {
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime taskCreateTime = task.getCreateTime();
                long diffHours = Duration.between(taskCreateTime, now).toHours();
                log.info("TaskId:{}, Priority:{}, task has not callback more than {} hours...", task.getId(), task.getPriority(), diffHours);
                TaskMqBo taskMqBo = new TaskMqBo();
                taskMqBo.setTaskId(task.getId());
                taskMqBo.setFinish(2);
                mqService.sendToTaskCallbackRepeat(taskMqBo, task.getPriority());
            }catch (Exception e){
                log.error("TaskId:{} timeout callback error", task.getId(), e);
            }
        }
    }
}
