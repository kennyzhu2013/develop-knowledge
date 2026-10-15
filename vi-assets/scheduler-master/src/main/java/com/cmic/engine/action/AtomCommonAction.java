package com.cmic.engine.action;

import com.cmic.config.xml.ActionXml;
import com.cmic.config.xml.AttributeXml;
import com.cmic.engine.event.EventOrigin;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.pojo.bo.Attribute;
import com.cmic.pojo.dto.ai.DyHttpRequest;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import com.cmic.service.JobService;
import com.cmic.service.KeywordService;
import com.cmic.service.ai.*;
import com.cmic.service.task.TaskService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.cmic.service.ai.VPService.*;

@Slf4j
@Service
public class AtomCommonAction implements Action {
    @Autowired
    private TaskService taskService;
    @Autowired
    private JobService jobService;

    @Autowired
    private ASRService asrService;
    @Autowired
    private VFDService vfdService;
    @Autowired
    private KeywordService keywordService;
    @Autowired
    private KeywordAnalysisService keywordAnalysisService;
    @Autowired
    private ScenarioService scenarioService;
    @Autowired
    private ScriptService scriptService;
    @Autowired
    private VPService vpService;
    @Autowired
    private EmotionService emotionService;
    @Autowired
    private KeywordRegexService keywordRegexService;
    @Autowired
    private IdentityService identityService;
    @Autowired
    private AIGSService aigsService;
    @Autowired
    private DeepSeekService deepSeekService;
    @Autowired
    private KeywordGroupService keywordGroupService;
    @Autowired
    private QwenService qwenService;
    @Autowired
    private RongtongService rongtongService;
    @Autowired
    private RTFraudReviewService rtFraudReviewService;
    @Autowired
    private CollectionService collectionService;
    @Autowired
    private TextSmlService textSmlService;
    @Autowired
    private MarketCallService marketCallService;
    @Autowired
    private KeywordRiskShieldService keywordRiskShieldService;
    @Autowired
    private DIFYService difyService;
    @Autowired
    private HttpModelService httpModelService;
    @Autowired
    private DealService dealService;
    @Autowired
    private RAGService ragService;
    @Autowired
    private BGEService bgeService;
    @Autowired
    private CQAntiService cqAntiService;
    @Autowired
    private CQAgentService cqAgentService;

    @Override
    public void validate(ActionXml obj) throws Exception {

    }

    @Override
    public void apply(ActionXml actionXml, EventOrigin eventOrigin, Object... obj) throws Exception {
        MqEntityEvent event = (MqEntityEvent) obj[0];
        String op = actionXml.getOp();
        if (op.equalsIgnoreCase("atom_initial")) {
            this.atomInitialHandler(event);
        }
        if (op.equalsIgnoreCase("atom_handler")) {
            this.atomHandler(actionXml, eventOrigin, obj);
        }
        if (op.equalsIgnoreCase("check_progress")) {
            this.checkProcess(event);
        }
    }

    public void atomInitialHandler(MqEntityEvent event) {
        SubTask subTask = event.getSubTask();
        Task task = event.getTask();

        String taskId = String.valueOf(task.getId());
        String jobId = String.valueOf(subTask.getId());
        Map<String, Object> attributes = task.getAttributes();

        if ("1".equals(attributes.get("transcription"))) {
            asrService.sendToMq(event);
        }
        if ("1".equals(attributes.get("forgeryDetection"))) {
            vfdService.sendToMq(event);
        }
        if ("1".equals(attributes.get("voiceComparison"))) {
            vpService.sendToMq(event, TYPE_1V1);
        }
        if ("1".equals(attributes.get("voiceAnalysis"))) {
            vpService.sendToMq(event, TYPE_1VN);
        }

        if (!"1".equals(attributes.get("transcription"))
            && !"1".equals(attributes.get("forgeryDetection"))
            && !"1".equals(attributes.get("voiceComparison"))
            && !"1".equals(attributes.get("voiceAnalysis"))) {
            log.info("TaskId:{}, jobId:{}, no atom model need to do", taskId, jobId);
            jobService.jobFinished(task, subTask, 0);
        }
    }

    @SneakyThrows
    public void atomHandler(ActionXml actionXml, EventOrigin eventOrigin, Object... obj) {
        MqEntityEvent event = (MqEntityEvent) obj[0];
        SubTask subTask = event.getSubTask();
        Task task = event.getTask();

        Map<String, Object> attributes = task.getAttributes();
        this.checkProcess(event);

        if ("1".equals(attributes.get("wordMatching"))) {
            keywordService.match(event);
        }
        if ("1".equals(attributes.get("sceneAnalysis"))) {
            scenarioService.sendToMq(event);
        }
        if ("1".equals(attributes.get("scriptConsistency"))) {
            scriptService.sendToMq(event);
        }
        if ("1".equals(attributes.get("sceneRecognition"))) {
            scenarioService.sendToMq(event);
        }
        if ("1".equals(attributes.get("fraudDetection"))) {
            keywordAnalysisService.sendToMq(event, KeywordAnalysisService.HIGH_THRESH);
        }
        if ("1".equals(attributes.get("emotionAnalysis"))) {
            emotionService.sendToMq(event);
        }
        if ("1".equals(attributes.get("regexWordMatching"))) {
            keywordRegexService.match(event);
        }
        if ("1".equals(attributes.get("identityComparison"))) {
            identityService.sendToMq(event);
        }
        if ("1".equals(attributes.get("aigsAnalysis"))) {
            aigsService.sendToMq(event);
        }
        if ("1".equals(attributes.get("deepSeekAnalysis"))) {
            Map<String, String> xmlAttribute = actionXml.getAttributeMap();
            DyHttpRequest request = deepSeekService.getRequest(task, subTask, xmlAttribute);
            deepSeekService.sendToMq(task, subTask, request);
        }
        if ("1".equals(attributes.get("wordAnalysis"))) {
            keywordGroupService.match(event);
        }
        if ("1".equals(attributes.get("qwenInference"))) {
            Map<String, String> xmlParam = actionXml.getAttributeMap();
            DyHttpRequest request = qwenService.getRequest(task, subTask, xmlParam);
            qwenService.sendToMq(task, subTask, request);
        }
        if ("1".equals(attributes.get("fusionFraudSceneCheck"))) {
            rongtongService.sendToMq(event);
        }
        if ("1".equals(attributes.get("fusionFraudReview"))) {
            rtFraudReviewService.sendToMq(event);
        }
        if ("1".equals(attributes.get("collectionModel"))) {
            collectionService.sendToMq(event);
        }
        if ("1".equals(attributes.get("contentSimilarityAnalyse"))) {
            textSmlService.sendToMq(event);
        }
        if ("1".equals(attributes.get("marketingCall"))) {
            marketCallService.sendToMq(event);
        }
        if ("1".equals(attributes.get("forbiddenWordCheck"))) {
            keywordRiskShieldService.match(event);
        }
        if ("1".equals(attributes.get("difyIntegration"))) {
            String taskId = String.valueOf(task.getId());
            String jobId = String.valueOf(subTask.getId());
            String model = actionXml.getAttributeValue("dify_model");
            AttributeXml asrTextXml = actionXml.getAttribute("asrText");

            String asrTextParam = asrTextXml==null?"text":asrTextXml.getValue().trim();

            Map<String, Object> customizedParam = (Map<String, Object>) task.getAttributes().get("difyParam");
            DyHttpRequest request = httpModelService.getRequest(taskId, jobId, model, customizedParam, asrTextParam);
            String asrText = (String)subTask.getResponseResults().get(Attribute.ASR_TEXT.getName());
            request.getRequestParameters().put(asrTextParam, asrText);

            difyService.sendToMq(task, subTask, request);
        }
        if ("1".equals(attributes.get("dealClass"))) {
            dealService.sendToMq(event);
        }
        if ("1".equals(attributes.get("ragSearch"))) {
            AttributeXml textXml = actionXml.getAttribute("text");
            String textParam = textXml == null ? Attribute.ASR_TEXT.getName() : textXml.getValue().trim();
            String text = (String) subTask.getResponseResults().get(textParam);

            AttributeXml kbTypeXml = actionXml.getAttribute("kbType");
            String kbType = kbTypeXml == null ? null : kbTypeXml.getValue().trim();
            ragService.sendToMq(event, text, kbType);
        }
        if ("1".equals(attributes.get("fraudDetectionLT"))) {
            keywordAnalysisService.sendToMq(event, KeywordAnalysisService.LOW_THRESH);
        }
        if ("1".equals(attributes.get("bgeClassifier"))) {
            bgeService.sendToMq(event);
        }
        if ("1".equals(attributes.get("cqFraudCheck"))) {
            cqAntiService.sendToMq(event);
        }
        if ("1".equals(attributes.get("cqAgentReview"))) {
            cqAgentService.sendToMq(event);
        }
    }

    public void checkProcess(MqEntityEvent event) {
        SubTask subTask = event.getSubTask();
        Task task = event.getTask();

        String taskId = String.valueOf(task.getId());
        String jobId = String.valueOf(subTask.getId());
        Map<String, Object> attributes = task.getAttributes();

        List<Boolean> statusList = new ArrayList<>();
        int count = 0;
        if ("1".equals(attributes.get("transcription"))) {
            count++;
            Object status = subTask.getResponseResults().get(Attribute.ASR_STATUS.getName());
            if (status != null) {
                statusList.add((Boolean) status);
            }
        }
        if ("1".equals(attributes.get("forgeryDetection"))) {
            count++;
            Object status = subTask.getResponseResults().get(Attribute.VFD_STATUS.getName());
            if (status != null) {
                statusList.add((Boolean) status);
            }
        }
        if ("1".equals(attributes.get("wordMatching"))) {
            count++;
            Object status = subTask.getResponseResults().get(Attribute.KEYWORD_STATUS.getName());
            if (status != null) {
                statusList.add((Boolean) status);
            }
        }
        if ("1".equals(attributes.get("sceneAnalysis"))) {
            count++;
            Object status = subTask.getResponseResults().get(Attribute.SCENARIO_STATUS.getName());
            if (status != null) {
                statusList.add((Boolean) status);
            }
        }
        if ("1".equals(attributes.get("scriptConsistency"))) {
            count++;
            Object status = subTask.getResponseResults().get(Attribute.SCRIPT_STATUS.getName());
            if (status != null) {
                statusList.add((Boolean) status);
            }
        }
        if ("1".equals(attributes.get("sceneRecognition"))) {
            count++;
            Object status = subTask.getResponseResults().get(Attribute.SCENARIO_STATUS.getName());
            if (status != null) {
                statusList.add((Boolean) status);
            }
        }
        if ("1".equals(attributes.get("fraudDetection"))) {
            count++;
            Object status = subTask.getResponseResults().get(Attribute.KEYWORD_ANALYSIS_STATUS.getName());
            if (status != null) {
                statusList.add((Boolean) status);
            }
        }
        if ("1".equals(attributes.get("voiceComparison"))) {
            count++;
            Object status = subTask.getResponseResults().get(Attribute.VP_STATUS.getName());
            if (status != null) {
                statusList.add((Boolean) status);
            }
        }
        if ("1".equals(attributes.get("voiceAnalysis"))) {
            count++;
            Object status = subTask.getResponseResults().get(Attribute.VP_STATUS.getName());
            if (status != null) {
                statusList.add((Boolean) status);
            }
        }
        if ("1".equals(attributes.get("emotionAnalysis"))) {
            count++;
            Object status = subTask.getResponseResults().get(Attribute.EMOTION_STATUS.getName());
            if (status != null) {
                statusList.add((Boolean) status);
            }
        }
        if ("1".equals(attributes.get("regexWordMatching"))) {
            count++;
            Object status = subTask.getResponseResults().get(Attribute.WORD_REGEX_STATUS.getName());
            if (status != null) {
                statusList.add((Boolean) status);
            }
        }
        if ("1".equals(attributes.get("identityComparison"))) {
            count++;
            Object status = subTask.getResponseResults().get(Attribute.IDENTITY_STATUS.getName());
            if (status != null) {
                statusList.add((Boolean) status);
            }
        }
        if ("1".equals(attributes.get("aigsAnalysis"))) {
            count++;
            Object status = subTask.getResponseResults().get(Attribute.AIGS_STATUS.getName());
            if (status != null) {
                statusList.add((Boolean) status);
            }
        }
        if ("1".equals(attributes.get("deepSeekAnalysis"))) {
            count++;
            Object status = subTask.getResponseResults().get(Attribute.DEEPSEEK_STATUS.getName());
            if (status != null) {
                statusList.add((Boolean) status);
            }
        }
        if ("1".equals(attributes.get("wordAnalysis"))) {
            count++;
            Object status = subTask.getResponseResults().get(Attribute.WORD_GROUP_STATUS.getName());
            if (status != null) {
                statusList.add((Boolean) status);
            }
        }
        if ("1".equals(attributes.get("qwenInference"))) {
            count++;
            Object status = subTask.getResponseResults().get(Attribute.QWEN_STATUS.getName());
            if (status != null) {
                statusList.add((Boolean) status);
            }
        }
        if ("1".equals(attributes.get("fusionFraudSceneCheck"))) {
            count++;
            Object status = subTask.getResponseResults().get(Attribute.RT_STATUS.getName());
            if (status != null) {
                statusList.add((Boolean) status);
            }
        }
        if ("1".equals(attributes.get("fusionFraudReview"))) {
            count++;
            Object status = subTask.getResponseResults().get(Attribute.RT_FRAUD_REVIEW_STATUS.getName());
            if (status != null) {
                statusList.add((Boolean) status);
            }
        }
        if ("1".equals(attributes.get("collectionModel"))) {
            count++;
            Object status = subTask.getResponseResults().get(Attribute.COLLECTION_STATUS.getName());
            if (status != null) {
                statusList.add((Boolean) status);
            }
        }
        if ("1".equals(attributes.get("contentSimilarityAnalyse"))) {
            count++;
            Object status = subTask.getResponseResults().get(Attribute.TEXT_SIMILARITY_STATUS.getName());
            if (status != null) {
                statusList.add((Boolean) status);
            }
        }
        if ("1".equals(attributes.get("marketingCall"))) {
            count++;
            Object status = subTask.getResponseResults().get(Attribute.MARKET_CALL_STATUS.getName());
            if (status != null) {
                statusList.add((Boolean) status);
            }
        }
        if ("1".equals(attributes.get("forbiddenWordCheck"))) {
            count++;
            Object status = subTask.getResponseResults().get(Attribute.KEYWORD_RS_STATUS.getName());
            if (status != null) {
                statusList.add((Boolean) status);
            }
        }
        if ("1".equals(attributes.get("difyIntegration"))) {
            count++;
            Object status = subTask.getResponseResults().get(Attribute.DIFY_STATUS.getName());
            if (status != null) {
                statusList.add((Boolean) status);
            }
        }
        if ("1".equals(attributes.get("dealClass"))) {
            count++;
            Object status = subTask.getResponseResults().get(Attribute.DEAL_STATUS.getName());
            if (status != null) {
                statusList.add((Boolean) status);
            }
        }
        if ("1".equals(attributes.get("ragSearch"))) {
            count++;
            Object status = subTask.getResponseResults().get(Attribute.RAG_STATUS.getName());
            if (status != null) {
                statusList.add((Boolean) status);
            }
        }
        if ("1".equals(attributes.get("fraudDetectionLT"))) {
            count++;
            Object status = subTask.getResponseResults().get(Attribute.KEYWORD_ANALYSIS_LT_STATUS.getName());
            if (status != null) {
                statusList.add((Boolean) status);
            }
        }
        if ("1".equals(attributes.get("bgeClassifier"))) {
            count++;
            Object status = subTask.getResponseResults().get(Attribute.BGE_STATUS.getName());
            if (status != null) {
                statusList.add((Boolean) status);
            }
        }
        if ("1".equals(attributes.get("cqFraudCheck"))) {
            count++;
            Object status = subTask.getResponseResults().get(Attribute.CQ_ANTI_STATUS.getName());
            if (status != null) {
                statusList.add((Boolean) status);
            }
        }
        if ("1".equals(attributes.get("cqAgentReview"))) {
            count++;
            Object status = subTask.getResponseResults().get(Attribute.CQ_AGENT_STATUS.getName());
            if (status != null) {
                statusList.add((Boolean) status);
            }
        }

        if (count != statusList.size()) {
            log.info("TaskId:{}, jobId:{}, waiting for other model response...", taskId, jobId);
            return;
        }
        boolean isAllTrue = statusList != null && !statusList.isEmpty() && statusList.stream().allMatch(Boolean::booleanValue);
        boolean isAllFalse = statusList != null && !statusList.isEmpty() && statusList.stream().noneMatch(Boolean::booleanValue);
        if (isAllTrue) {
            jobService.jobFinished(task, subTask, 1);
        } else if(isAllFalse) {
            jobService.jobFinished(task, subTask, 0);
        } else {
            jobService.jobFinished(task, subTask, 2);
        }
    }
}
