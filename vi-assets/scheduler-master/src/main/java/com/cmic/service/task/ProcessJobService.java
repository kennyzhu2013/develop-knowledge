package com.cmic.service.task;

import cn.hutool.core.util.StrUtil;
import com.cmic.dao.QaDetailDaoImpl;
import com.cmic.engine.XmlEventPublisher;
import com.cmic.engine.event.MqEvent;
import com.cmic.pojo.bo.AIModelEnum;
import com.cmic.pojo.bo.Attribute;
import com.cmic.pojo.dto.ErrorCode;
import com.cmic.pojo.entity.QaDetail;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import com.cmic.service.RedisService;
import com.cmic.util.JacksonUtil;
import com.cmic.util.RedisUtil;
import com.cmic.util.TraceIdUtil;
import io.prometheus.client.Counter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProcessJobService {
    @Resource
    private XmlEventPublisher xmlEventPublisher;
    @Autowired
    private SubTaskService subTaskService;
    @Autowired
    private Counter job_send_error;
    @Autowired
    private RedisService redisService;
    @Autowired
    private QaDetailDaoImpl qaDetailDao;

//    @Transactional
    public void processJob(Task task, List<SubTask> subTaskList) {
        try {
            this.updateIfReuseAsrResult(task, subTaskList);
        }catch (Exception e){
            log.error("TaskId:" + task.getId() + ", query reuse asr from ck error:", e);
        }

        for (SubTask subTask : subTaskList) {
            String jobId = String.valueOf(subTask.getId());
            TraceIdUtil.setTraceId(StrUtil.join("-",task.getTransactionId(),jobId));
            int status;
            try {
                Object text = subTask.getAttributes().get("text");
                if (text != null) {
//                    subTask.getResponseResults().put(Attribute.ASR_TEXT.getName(), text.toString());
                    Map<String, Object> responseMap = new HashMap<>();
                    responseMap.put(Attribute.ASR_TEXT.getName(), text.toString());
                    subTaskService.updateSubTaskResponseMap(subTask, responseMap);
                }
                xmlEventPublisher.mqEventPublish(MqEvent.INITIAL, task , subTask);
                status = 2;
            } catch (Exception e) {
                //TODO 失败记录状态，后续看情况是否需要重试
                job_send_error.inc();
                status = 1;
                log.error("TaskId:" + task.getId() + ", jobId:" + jobId + ", send job to mq error:", e);
            }
            log.info("TaskId:{}, jobId:{}, init event end", task.getId(), subTask.getId());
            subTaskService.updateSubTaskStatus(Long.parseLong(jobId), status);
            log.info("TaskId:{}, jobId:{}, process subTask success:{}", task.getId(), subTask.getId(), subTask);
        }
    }

    public void updateIfReuseAsrResult(Task task, List<SubTask> subTaskList) {
        // 筛选有reuse标识的dataId
        List<String> dataIds = subTaskList.stream()
                .filter(subTask -> {
                    Integer reuse = (Integer) subTask.getAttributes().get("reuse");
                    return Objects.nonNull(reuse) && reuse == 1;
                })
                .map(SubTask::getDataId)
                .collect(Collectors.toList());
        if (dataIds.isEmpty()) {
            return;
        }
        // redis过滤后读CK
        dataIds = redisService.filterDataId(dataIds);
        List<QaDetail> detailList = qaDetailDao.findByDataIds(dataIds);
        // 先过滤content不为空再去重
        Map<String, QaDetail> detailMap = detailList.stream()
                .filter(detail -> StringUtils.hasLength(detail.getContent()))
                .collect(Collectors.toMap(QaDetail::getDataId, detail -> detail,
                        // 合并函数：当dataId重复时，保留create_time较晚的元素
                        (existing, replacement) -> {
                            if (existing.getCreateTime().isAfter(replacement.getCreateTime())) {
                                return existing;
                            }
                            return replacement;
                        }
                ));
        log.info("TaskId:{}, redis match {} url, db have {} result", task.getId(), dataIds.size(), detailMap.size());
        // update subTask
        for (SubTask subTask : subTaskList) {
            String dataId = subTask.getDataId();
            if (!detailMap.containsKey(dataId)) {
                continue;
            }
            subTaskService.beforeSendModel(task, subTask, AIModelEnum.ASR.getName());
            QaDetail detail = detailMap.get(dataId);
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put(Attribute.ASR_STATUS.getName(), true);
            responseMap.put(Attribute.ASR_TEXT.getName(), detail.getContent());
            responseMap.put(Attribute.ASR_DURATION.getName(), detail.getDuration());
            responseMap.put(Attribute.ASR_INFO.getName(), JacksonUtil.string2Obj(detail.getAsrInfo(), Map.class));
            subTaskService.updateSubTaskResponseMap(subTask, responseMap);
        }
    }
}
