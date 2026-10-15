package com.cmic.service.ai;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import com.cmic.config.metrics.CommonMetrics;
import com.cmic.dao.QaDetailDaoImpl;
import com.cmic.engine.XmlEventPublisher;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.engine.event.MqEvent;
import com.cmic.pojo.bo.AIModelEnum;
import com.cmic.pojo.bo.Attribute;
import com.cmic.pojo.dto.BaseResponse;
import com.cmic.pojo.dto.CallbackModelRequest;
import com.cmic.pojo.dto.ErrorCode;
import com.cmic.pojo.dto.ai.ASRRequest;
import com.cmic.pojo.dto.ai.ASRResponse;
import com.cmic.pojo.entity.QaDetail;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import com.cmic.service.MqService;
import com.cmic.service.RedisService;
import com.cmic.service.task.SubTaskService;
import com.cmic.service.task.TaskService;
import com.cmic.util.JacksonUtil;
import com.cmic.util.ModelUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cglib.beans.BeanMap;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class ASRService implements IMqService {
    @Autowired
    private MqService mqService;
    @Autowired
    private XmlEventPublisher xmlEventPublisher;
    @Resource
    private TaskService taskService;
    @Autowired
    private SubTaskService subTaskService;
    @Autowired
    private RedisService redisService;
    @Autowired
    private QaDetailDaoImpl qaDetailDao;
    @Autowired
    private CommonMetrics commonMetrics;

    public void sendToMq(MqEntityEvent event) {
        SubTask subTask = event.getSubTask();
        Task task = event.getTask();

        String taskId = String.valueOf(task.getId());
        String jobId = String.valueOf(subTask.getId());

        Map<String, Object> attributes = task.getAttributes();
        Map<String, Object> subTaskAttributes = subTask.getAttributes();

        if (reuseAsrResult(task, subTask)) {
            return;
        }

        String type = (String) attributes.get("transcriptionType");
        String language = (String) attributes.get("language");

        type = StringUtils.hasLength(type) ? type : "0";
        language = StringUtils.hasLength(language) ? language : "zh";


        ASRRequest asrRequest = ASRRequest.builder()
                .jobId(ModelUtil.buildModelJobId(jobId, subTask.getDataId()))
                .fileUrl((String) subTaskAttributes.get("url"))
                .type(type)
                .language(language)
                .build();
        String emotionTag = (String) attributes.get("emotionTag");
        if (StringUtils.hasLength(emotionTag)) {
            asrRequest.setEmotionTag(Boolean.valueOf(emotionTag));
        }

        subTaskService.beforeSendModel(task, subTask, AIModelEnum.ASR.getName());

        Integer prior = (Integer) attributes.get("asrPrior");
        if (Objects.nonNull(prior) && prior.equals(1)) {
            log.info("TaskId:{}, jobId:{}, processId:{}, priority:{}, send to fast asr", taskId, jobId, task.getProcessId(), task.getPriority());
            mqService.sendToFastASR(subTask.getLocation(), asrRequest, task.getPriority());
        } else {
            log.info("TaskId:{}, jobId:{}, processId:{}, priority:{}, send to asr", taskId, jobId, task.getProcessId(), task.getPriority());
            mqService.sendToASR(subTask.getLocation(), asrRequest, task.getPriority());
        }
        log.info("TaskId:{}, jobId:{}, processId:{}, send to asr mq success", taskId, jobId, task.getProcessId());
    }

    public void handlerMessage(Task task, SubTask subTask, BaseResponse<?> baseResponse) {
        String code = baseResponse.getCode();
        String message = baseResponse.getMessage();
        ASRResponse response = JacksonUtil.mapToBean((Map) baseResponse.getData(), ASRResponse.class);

        log.info("TaskId:{}, jobId:{}, processId:{}, Asr response, code:{}, message:{}", task.getId(), subTask.getId(), task.getProcessId(), code, message);


        Map<String, Object> responseMap = new HashMap<>();
        if (ErrorCode.SUCCESS.equals(code)) {
            String text = response.getText();
            long audioDuration = response.getAudioDuration();
            ASRResponse.Info info = response.getInfo();

            responseMap.put(Attribute.ASR_STATUS.getName(), true);
            responseMap.put(Attribute.ASR_TEXT.getName(), text);
            responseMap.put(Attribute.ASR_DURATION.getName(), audioDuration);
            responseMap.put(Attribute.ASR_INFO.getName(), JacksonUtil.beanToMap(info));
            // 缓存处理成功的dataId
            Integer reuse = (Integer) subTask.getAttributes().get("reuse");
            if (!Objects.isNull(reuse) && reuse == 1) {
                Integer expire = (Integer) task.getAttributes().get("expire");
                redisService.setDataId(subTask.getDataId(), expire);
            }
            if (audioDuration <= 0) {
                commonMetrics.modelAbnormalInc(AIModelEnum.ASR.getName(), task.getProcessId(), "zero_duration");
            }
        }else {
            responseMap.put(Attribute.ASR_STATUS.getName(), false);
        }

        subTask = subTaskService.afterResponseModel(code, message, task, subTask, AIModelEnum.ASR.getName(), responseMap);

        xmlEventPublisher.mqEventPublish(MqEvent.ASR_RESPONSE, task, subTask);
    }

    public boolean reuseAsrResult(Task task, SubTask subTask) {
        Integer reuse = (Integer) subTask.getAttributes().get("reuse");
        Boolean asrStatus = (Boolean) subTask.getResponseResults().get(Attribute.ASR_STATUS.getName());
        if (Objects.isNull(reuse) || reuse != 1) {
            return false;
        }
        if (Objects.isNull(asrStatus) || !asrStatus) {
            log.info("TaskId:{}, jobId:{}, asr have not result", task.getId(), subTask.getId());
            return false;
        }
        log.info("TaskId:{}, jobId:{}, asr reuse result", task.getId(), subTask.getId());
        subTask = subTaskService.afterResponseModel(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), task, subTask, AIModelEnum.ASR.getName(), null);

        xmlEventPublisher.mqEventPublish(MqEvent.ASR_RESPONSE, task, subTask);
        return true;
    }

    public SubTask updateSubTaskAsrText(SubTask subTask, String asrText, Long asrDuration){
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put(Attribute.ASR_TEXT.getName(), asrText);
        responseMap.put(Attribute.ASR_DURATION.getName(), asrDuration);
        subTaskService.updateSubTaskResponseMap(subTask, responseMap);
        return subTask;
    }
}
