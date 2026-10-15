package com.cmic.service.ai;

import com.cmic.engine.XmlEventPublisher;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.engine.event.MqEvent;
import com.cmic.pojo.bo.AIModelEnum;
import com.cmic.pojo.bo.Attribute;
import com.cmic.pojo.dto.ErrorCode;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import com.cmic.service.MqService;
import com.cmic.service.task.SubTaskService;
import com.cmic.service.task.TaskService;
import com.cmic.util.JacksonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class KeywordGroupService{
    @Autowired
    private MqService mqService;
    @Autowired
    private XmlEventPublisher xmlEventPublisher;
    @Resource
    private TaskService taskService;
    @Autowired
    private SubTaskService subTaskService;

    public void match(MqEntityEvent event) {
        Task task = event.getTask();
        SubTask subTask = event.getSubTask();

        subTaskService.beforeSendModel(task, subTask, AIModelEnum.WORD_GROUP.getName());
        String code = ErrorCode.SUCCESS.getCode();
        String message = ErrorCode.SUCCESS.getMessage();
        Map<String, Object> responseMap = new HashMap<>();
        try {
            String asrText = (String)subTask.getResponseResults().get(Attribute.ASR_TEXT.getName());

            Map<String, Object> attributes = task.getAttributes();
            List<List<List<String>>> wordGroups = (List<List<List<String>>>) attributes.get("wordGroup");
            String result = getMatchResult(wordGroups, asrText);
            log.info("TaskId:{}, jobId:{}, processId:{}, priority:{}, keyword group success", task.getId(), subTask.getId(), task.getProcessId(), task.getPriority());

//            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put(Attribute.WORD_GROUP_STATUS.getName(), true);
            responseMap.put(Attribute.WORD_GROUP_RESULT.getName(), result);
//            subTaskService.updateSubTaskResponseMap(subTask, responseMap);
        }catch (Exception e){
            log.error("TaskId:{}, jobId:{}, processId:{}, keyword group failed",task.getId(), subTask.getId(), task.getProcessId(), e);
            code = ErrorCode.ERROR.getCode();
            message = ErrorCode.ERROR.getMessage();
            responseMap.put(Attribute.WORD_GROUP_STATUS.getName(), false);
//            subTaskService.updateSubTaskErrorMessage(subTask, AIModelEnum.WORD_GROUP.getName(), "matched unknown error");
//            subTaskService.updateSubTaskResponseResult(subTask, Attribute.WORD_GROUP_STATUS.getName(), false);
        }
        subTask = subTaskService.afterResponseModel(code, message, task, subTask, AIModelEnum.WORD_GROUP.getName(), responseMap);
        xmlEventPublisher.mqEventPublish(MqEvent.WORD_GROUP_RESPONSE, task, subTask);
    }

    private static String getMatchResult(List<List<List<String>>> wordGroups, String text) {
        for (List<List<String>> wordGroup : wordGroups) {
            Pattern pattern = generatePatterns(wordGroup, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return "1";
            }
        }
        return "0";
    }

    /**
     * 生成正则表达式模式
     */
    public static Pattern generatePatterns(List<List<String>> groups, int flags) {
        List<String> groupPatterns = new ArrayList<>();

        for (List<String> words : groups) {
            List<String> escapedWords = new ArrayList<>();
            // 转义特殊字符
            for (String word : words) {
                escapedWords.add(Pattern.quote(word));
            }

            // 为每个组创建正向断言模式
            List<String> lookaheads = new ArrayList<>();
            for (String word : escapedWords) {
                lookaheads.add("(?=.*" + word + ")");
            }

            // 每个组的模式
            String groupPattern = "(" + String.join("", lookaheads) + ".*)";
            groupPatterns.add(groupPattern);
        }

        // 组合所有组模式，使用|分隔
        String combinedPattern = String.join("|", groupPatterns);
        return Pattern.compile(combinedPattern, flags);
    }


    // 使用示例
    public static void main(String[] args) {
        // 测试组
        List<List<List<String>>> testGroups = new ArrayList<>();
        List<List<String>> testGroup = new ArrayList<>();
        testGroup.add(List.of("航班","延误","理赔","浏览器"));                  // 索引0
        testGroups.add(testGroup);
        // 生成模式（不区分大小写）
        Pattern pattern = generatePatterns(testGroup, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Pattern pattern2 = generatePatterns(testGroup, Pattern.CASE_INSENSITIVE); // 导致.*无法跨换行匹配

        // 测试文本
        List<String> testTexts = List.of(
                "航班延误\n后在浏览器\n申请理赔",
                "航班延误后在浏览器申请理赔"
        );

        // 检查匹配并输出结果
        for (String text : testTexts) {
            System.out.println("文本: " + text);
            String matchResult = getMatchResult(testGroups, text);
            System.out.println(matchResult);
        }
    }
}
