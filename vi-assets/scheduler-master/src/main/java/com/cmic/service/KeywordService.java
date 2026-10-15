package com.cmic.service;

import com.cmic.engine.XmlEventPublisher;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.engine.event.MqEvent;
import com.cmic.pojo.bo.AIModelEnum;
import com.cmic.pojo.bo.Attribute;
import com.cmic.pojo.bo.KeywordBo;
import com.cmic.pojo.dto.ErrorCode;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import com.cmic.service.task.SubTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KeywordService {
    @Autowired
    private XmlEventPublisher xmlEventPublisher;
    @Autowired
    private SubTaskService subTaskService;

    public List<KeywordBo> match(MqEntityEvent event){
        Task task = event.getTask();
        SubTask subTask = event.getSubTask();
        subTaskService.beforeSendModel(task, subTask, AIModelEnum.KEYWORD.getName());

        String code = ErrorCode.SUCCESS.getCode();
        String message = ErrorCode.SUCCESS.getMessage();
        Map<String, Object> responseMap = new HashMap<>();
        List<KeywordBo> matchedKeywords = new ArrayList<>();
        try {
            String asrText = (String)subTask.getResponseResults().get(Attribute.ASR_TEXT.getName());

            Map<String, Object> attributes = task.getAttributes();
            List<Map<String, Object>> wordPackage = (List<Map<String, Object>>) attributes.get("wordPackage");
            List<KeywordBo> keywordBoList = wordPackage.stream()
                    .map(map -> KeywordBo.builder()
                            .packageId(Integer.parseInt(map.get("packageId").toString()))
                            .word((String) map.get("word"))
                            .build())
                    .collect(Collectors.toList());
            matchedKeywords = matchKeywords(keywordBoList, asrText);

            log.info("TaskId:{}, jobId:{}, processId:{}, priority:{}, keyword match success", task.getId(), subTask.getId(), task.getProcessId(), task.getPriority(), matchedKeywords);

            responseMap.put(Attribute.KEYWORD_STATUS.getName(), true);
            responseMap.put(Attribute.KEYWORD_MATCHED.getName(), matchedKeywords);
        } catch (Exception e) {
            log.error("TaskId:{}, jobId:{}, processId:{}, keyword match failed:", task.getId(), subTask.getId(), task.getProcessId(), e);
            code = ErrorCode.ERROR.getCode();
            message = ErrorCode.ERROR.getMessage();
            responseMap.put(Attribute.KEYWORD_STATUS.getName(), false);
        }

        subTask = subTaskService.afterResponseModel(code, message, task, subTask, AIModelEnum.KEYWORD.getName(), responseMap);
        xmlEventPublisher.mqEventPublish(MqEvent.KEYWORD_RESPONSE, task, subTask);
        return matchedKeywords;
    }

    /**
     * match keyword
     * @param wordPackage
     * @param text
     * @return
     */
    public List<KeywordBo> matchKeywords(List<KeywordBo> wordPackage, String text){
        List<KeywordBo> list = new ArrayList<>();
        for(KeywordBo keywordBo: wordPackage){
            String[] words = keywordBo.getWord().split(",");
            String matchWord = this.matchKeywords(words, text);
            if(StringUtils.hasLength(matchWord)){
                keywordBo.setPackageId(keywordBo.getPackageId());
                keywordBo.setWord(matchWord);
                list.add(keywordBo);
            }
        }
        return list;
    }

    private String matchKeywords(String[] keywords, String text) {
        List<String> hitKeywords = new ArrayList<>();

        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                hitKeywords.add(keyword);
            }
        }
        String hitWords = String.join(",", hitKeywords);

        return hitWords;
    }
}
