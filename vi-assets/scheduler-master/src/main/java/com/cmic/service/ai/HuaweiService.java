package com.cmic.service.ai;

import com.cmic.engine.XmlEventPublisher;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.engine.event.MqEvent;
import com.cmic.pojo.bo.AIModelEnum;
import com.cmic.pojo.bo.Attribute;
import com.cmic.pojo.dto.BaseResponse;
import com.cmic.pojo.dto.ErrorCode;
import com.cmic.pojo.dto.ai.HuaweiRequest;
import com.cmic.pojo.dto.ai.HuaweiResponse;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import com.cmic.service.MqService;
import com.cmic.service.task.SubTaskService;
import com.cmic.util.JacksonUtil;
import com.cmic.util.ModelUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class HuaweiService implements IMqService{
    @Autowired
    private MqService mqService;
    @Autowired
    private XmlEventPublisher xmlEventPublisher;
    @Autowired
    private SubTaskService subTaskService;
    @Value("${huawei.qc.appKey}")
    private String appKey;
    @Value("${huawei.qc.serviceType}")
    private String serviceType;
    @Override
    public void sendToMq(MqEntityEvent event) {

    }

    public void sendToMq(MqEntityEvent event, int type) {
        SubTask subTask = event.getSubTask();
        Task task = event.getTask();

        String taskId = String.valueOf(task.getId());
        String jobId = String.valueOf(subTask.getId());

        Map<String, Object> attributes = task.getAttributes();
        String industryCategory = (String) attributes.get("industryCategory");
        String industrySegmentation = (String) attributes.get("industrySegmentation");

        Map<String, Object> subTaskAttributes = subTask.getAttributes();
        HuaweiRequest request = HuaweiRequest.builder()
                .jobId(ModelUtil.buildModelJobId(jobId, subTask.getDataId()))
                .appKey(appKey)
                .serviceType(serviceType)
                .checkType(type)
                .sessionId(jobId)
                .build();
        if(type == 0){
            String url = (String) subTaskAttributes.get("url");
            request.setRecordUrl(url);
        }else {
            String asrText = (String)subTask.getResponseResults().get(Attribute.ASR_TEXT.getName());
            request.setRecordText(asrText);
        }
        if(StringUtils.hasLength(industryCategory)){
            request.setIndustryCategory(industryCategory);
        }
        if(StringUtils.hasLength(industrySegmentation)){
            request.setIndustrySegmentation(industrySegmentation);
        }
        subTaskService.beforeSendModel(task, subTask, AIModelEnum.HUAWEI_QC.getName());
        log.info("TaskId:{}, jobId:{}, processId:{}, priority:{}, send to huaweiQc", taskId, jobId, task.getProcessId(), task.getPriority());
        mqService.sendToHuawei(subTask.getLocation(), request, task.getPriority());
    }

    @Override
    public void handlerMessage(Task task, SubTask subTask, BaseResponse<?> baseResponse) {
        String code = baseResponse.getCode();
        String message = baseResponse.getMessage();
        HuaweiResponse response = JacksonUtil.mapToBean((Map) baseResponse.getData(), HuaweiResponse.class);

        log.info("TaskId:{}, jobId:{}, processId:{}, Huawei response, code:{}, message:{}", task.getId(), subTask.getId(), task.getProcessId(), code, baseResponse.getMessage());

        Map<String, Object> responseMap = new HashMap<>();
        if(ErrorCode.SUCCESS.equals(code)) {
//            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put(Attribute.HUAWEI_QC_STATUS.getName(), true);
            responseMap.put(Attribute.HUAWEI_QC_RESULT.getName(), response.getGeneralDetectionResult());
            responseMap.put(Attribute.HUAWEI_QC_DETAIL.getName(), response.getGeneralDetectionDetail());
            responseMap.put(Attribute.HUAWEI_QC_CONSISTENCY.getName(), response.getSceneConsistencyDetection());
//            subTaskService.updateSubTaskResponseMap(subTask, responseMap);
        } else {
            responseMap.put(Attribute.HUAWEI_QC_STATUS.getName(), false);
//            subTaskService.updateSubTaskErrorMessage(subTask, AIModelEnum.HUAWEI_QC.getName(), baseResponse.getMessage());
//            subTaskService.updateSubTaskResponseResult(subTask, Attribute.HUAWEI_QC_STATUS.getName(), false);
        }
        subTask = subTaskService.afterResponseModel(code, message, task, subTask, AIModelEnum.HUAWEI_QC.getName(), responseMap);
        xmlEventPublisher.mqEventPublish(MqEvent.HUAWEI_RESPONSE, task, subTask);
    }
}
