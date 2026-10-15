package com.cmic.service.ai;

import com.cmic.engine.XmlEventPublisher;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.engine.event.MqEvent;
import com.cmic.pojo.bo.AIModelEnum;
import com.cmic.pojo.bo.Attribute;
import com.cmic.pojo.dto.BaseResponse;
import com.cmic.pojo.dto.ErrorCode;
import com.cmic.pojo.dto.ai.RAGRequest;
import com.cmic.pojo.dto.ai.RAGResponse;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import com.cmic.service.MqService;
import com.cmic.service.task.SubTaskService;
import com.cmic.util.JacksonUtil;
import com.cmic.util.ModelUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class RAGService implements IMqService {
    @Autowired
    private MqService mqService;
    @Autowired
    private XmlEventPublisher xmlEventPublisher;
    @Autowired
    private SubTaskService subTaskService;

    @Override
    public void sendToMq(MqEntityEvent event) {
    }

    public void sendToMq(MqEntityEvent event, String text, String kbType) {
        SubTask subTask = event.getSubTask();
        Task task = event.getTask();

        String taskId = String.valueOf(task.getId());
        String jobId = String.valueOf(subTask.getId());

        Map<String, Object> attributes = task.getAttributes();
        Integer topK = (Integer) attributes.get("ragTopK");

        RAGRequest request = RAGRequest.builder()
                .jobId(ModelUtil.buildModelJobId(jobId, subTask.getDataId()))
                .text(text)
                .topK(topK)
                .kbType(kbType)
                .build();

        subTaskService.beforeSendModel(task, subTask, AIModelEnum.RAG.getName());
        log.info("TaskId:{}, jobId:{}, processId:{}, priority:{}, send to rag", taskId, jobId, task.getProcessId(), task.getPriority());
        mqService.sendToRAG(subTask.getLocation(), request, task.getPriority());
    }

    @Override
    public void handlerMessage(Task task, SubTask subTask, BaseResponse<?> baseResponse) {
        String code = baseResponse.getCode();
        String message = baseResponse.getMessage();
        RAGResponse response = JacksonUtil.mapToBean((Map) baseResponse.getData(), RAGResponse.class);

        log.info("TaskId:{}, jobId:{}, processId:{}, rag response, code:{}, message:{}", task.getId(), subTask.getId(), task.getProcessId(), code, baseResponse.getMessage());

        Map<String, Object> responseMap = new HashMap<>();
        if (ErrorCode.SUCCESS.equals(code)) {
//            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put(Attribute.RAG_STATUS.getName(), true);
            responseMap.put(Attribute.RAG_RESULTS.getName(), response.getResults());
            responseMap.put(Attribute.RAG_COUNT.getName(), response.getCount());
//            subTaskService.updateSubTaskResponseMap(subTask, responseMap);
        } else {
            responseMap.put(Attribute.RAG_STATUS.getName(), false);
//            subTaskService.updateSubTaskErrorMessage(subTask, AIModelEnum.RAG.getName(), baseResponse.getMessage());
//            subTaskService.updateSubTaskResponseResult(subTask, Attribute.RAG_STATUS.getName(), false);
        }
        subTask = subTaskService.afterResponseModel(code, message, task, subTask, AIModelEnum.RAG.getName(), responseMap);
        xmlEventPublisher.mqEventPublish(MqEvent.RAG_RESPONSE, task, subTask);
    }
}
