package com.cmic.service;

import com.cmic.config.xml.ActionXml;
import com.cmic.config.xml.AttributeXml;
import com.cmic.engine.strategy.AttributeOverrideStrategy;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import com.cmic.service.task.SubTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AttributeService {
    @Autowired
    private RedisService redisService;
    @Autowired
    private SubTaskService subTaskService;

    // 自动注入所有的覆盖策略 Bean
    @Autowired
    private List<AttributeOverrideStrategy> overrideStrategies;

    public void setValue(Task task, SubTask subTask, ActionXml actionXml, List<AttributeXml> attributes){
        String type = actionXml.getType();  // type 1-job callback属性 model-job ModelInfo里的属性
        String data = actionXml.getData();

        if("1".equals(type)){
            setAttributeJobCallback(subTask.getId(), attributes);
            log.info("TaskId:{}, jobId:{}, set attribute success:{}", task.getId(), subTask.getId(), attributes);
        } else if (type.equals("2")) {
            Map<String,Object> attributeMap = attributes.stream()
                    .filter(attr -> attr != null && attr.getName() != null)
                    .collect(Collectors.toMap(
                            AttributeXml::getName,
                            attr -> attr.getValue() != null ? attr.getValue() : "",
                            (existing, replacement) -> replacement // 处理重复key，保留最后一个值
                    ));
            String callId = subTask.getDataId();
            redisService.setAttributeCallId(callId, attributeMap);
            log.info("TaskId:{}, jobId:{}, transactionId:{}, set attribute success:{}",
                    task.getId(), subTask.getId(), task.getTransactionId(), attributes);
        }else if ("model".equals(type)){
            setAttributeJobModelInfo(subTask, data, attributes);
            log.info("TaskId:{}, jobId:{}, set model {} attribute success:{}", task.getId(), subTask.getId(), data, attributes);
        }

    }

    private void setAttributeJobCallback(long jobId, List<AttributeXml> attributes){
        Map<String,Object> newAttrs = attributes.stream()
                .filter(attr -> attr != null && attr.getName() != null)
                .collect(Collectors.toMap(
                        AttributeXml::getName,
                        attr -> attr.getValue() != null ? attr.getValue() : "",
                        (existing, replacement) -> replacement // 处理重复key，保留最后一个值
                ));
        Map<String, Object> existingAttrs = redisService.getAttributeJobCallback(jobId);
        Map<String, Object> finalUpdateAttrs = this.getAttributeUpdate(newAttrs, existingAttrs);

        redisService.setAttributeJobCallback(jobId, finalUpdateAttrs);
    }

    public Map<String, Object> getAttributeUpdate(Map<String, Object> newAttrs, Map<String, Object> existingAttrs) {
        existingAttrs = existingAttrs == null ? new HashMap<>() : existingAttrs;
        Map<String, Object> finalUpdateAttrs = new HashMap<>();

        for (Map.Entry<String, Object> entry : newAttrs.entrySet()) {
            String attrName = entry.getKey();
            Object newValue = entry.getValue();
            Object oldValue = existingAttrs.get(attrName);
            // 查找支持该属性的策略
            AttributeOverrideStrategy strategy = overrideStrategies.stream()
                    .filter(s -> s.supports(attrName))
                    .findFirst()
                    .orElse(null);
            boolean canOverride = true;
            if (strategy != null) {
                canOverride = strategy.allowOverride(newValue, oldValue);
            }

            if (canOverride) {
                finalUpdateAttrs.put(attrName, newValue);
            }
        }
        return finalUpdateAttrs;
    }

    private void setAttributeJobModelInfo(SubTask subTask, String model, List<AttributeXml> attributes){
        Map<String, Object> newAttrs = attributes.stream()
                .filter(attr -> attr != null && attr.getName() != null)
                .collect(Collectors.toMap(
                        AttributeXml::getName,
                        attr -> attr.getValue() != null ? attr.getValue() : "",
                        (existing, replacement) -> replacement // 处理重复key，保留最后一个值
                ));
        subTaskService.updateSubTaskModelAttribute(subTask, model, newAttrs);
    }
}
