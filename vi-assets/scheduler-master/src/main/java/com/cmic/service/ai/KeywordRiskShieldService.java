package com.cmic.service.ai;

import com.cmic.engine.XmlEventPublisher;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.engine.event.MqEvent;
import com.cmic.pojo.bo.AIModelEnum;
import com.cmic.pojo.bo.Attribute;
import com.cmic.pojo.dto.ErrorCode;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import com.cmic.service.task.SubTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class KeywordRiskShieldService {
    @Autowired
    private XmlEventPublisher xmlEventPublisher;
    @Autowired
    private SubTaskService subTaskService;

    public void match(MqEntityEvent event){
        Task task = event.getTask();
        SubTask subTask = event.getSubTask();

        subTaskService.beforeSendModel(task, subTask, AIModelEnum.KEYWORD_RS.getName());
        String code = ErrorCode.SUCCESS.getCode();
        String message = ErrorCode.SUCCESS.getMessage();
        Map<String, Object> responseMap = new HashMap<>();
        try {
            String asrText = (String)subTask.getResponseResults().get(Attribute.ASR_TEXT.getName());

            Map<String, Object> attributes = task.getAttributes();
            List<Map<String, String>> wordRisk = (List<Map<String, String>>) attributes.get("wordRisk");
            List<Map<String, String>> results = getMatchResult(wordRisk, asrText);
            log.info("TaskId:{}, jobId:{}, processId:{}, priority:{}, keyword risk shield success", task.getId(), subTask.getId(), task.getProcessId(), task.getPriority(), results);

//            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put(Attribute.KEYWORD_RS_STATUS.getName(), true);
            responseMap.put(Attribute.KEYWORD_RS_RESULT.getName(), results);
//            subTaskService.updateSubTaskResponseMap(subTask, responseMap);
        }catch (Exception e){
            log.error("TaskId:{}, jobId:{}, processId:{}, keyword risk shield failed:", task.getId(), subTask.getId(), task.getProcessId(), e);
            code = ErrorCode.ERROR.getCode();
            message = ErrorCode.ERROR.getMessage();
            responseMap.put(Attribute.KEYWORD_RS_STATUS.getName(), false);
//            subTaskService.updateSubTaskErrorMessage(subTask, AIModelEnum.KEYWORD_RS.getName(), "matched unknown error");
//            subTaskService.updateSubTaskResponseResult(subTask, Attribute.KEYWORD_RS_STATUS.getName(), false);
        }
        subTask = subTaskService.afterResponseModel(code, message, task, subTask, AIModelEnum.KEYWORD_RS.getName(), responseMap);
        xmlEventPublisher.mqEventPublish(MqEvent.KEYWORD_RS_RESPONSE, task, subTask);
    }

    private List<Map<String, String>> getMatchResult(List<Map<String, String>> wordRisk, String text){
        List<Map<String, String>> results = new ArrayList<>();
        for(Map<String, String> map: wordRisk){
            String type = map.get("type");
            String[] words = map.get("word").split(",");
            String matched = this.matchKeywordRiskShield(words, text);

            Map<String , String> resultMap = new HashMap<>();
            resultMap.put("type", type);
            resultMap.put("result", matched);
            results.add(resultMap);
        }
        return results;
    }

    private String matchKeywordRiskShield(String[] keywords, String text) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return "1";
            }
        }

        return "0";
    }
}
