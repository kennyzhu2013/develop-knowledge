package com.cmic.service.task;

import com.cmic.dao.ShardTaskDaoImpl;
import com.cmic.engine.XmlEventPublisher;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.engine.event.MqEvent;
import com.cmic.pojo.bo.Attribute;
import com.cmic.pojo.entity.ShardTask;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import com.cmic.service.ai.ASRService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Slf4j
@Service
public class ShardTaskService {
    @Autowired
    private ShardTaskDaoImpl shardTaskDao;
    @Autowired
    private XmlEventPublisher xmlEventPublisher;
    @Autowired
    private SubTaskService subTaskService;
    @Autowired
    private ASRService asrService;

    public void sendShard(MqEntityEvent event) {
        SubTask subTask = event.getSubTask();
        Task task = event.getTask();

        String taskId = String.valueOf(task.getId());
        long jobId = subTask.getId();


        Map<String, Object> subTaskAttributes = subTask.getAttributes();
        String callId = subTask.getDataId();
        boolean isStream = task.isStream();
        if(!isStream){
            log.info("TaskId:{}, jobId:{}, callId:{} is not stream, break",
                    taskId, jobId, callId);
            return;
        }
        int segmentId = Integer.valueOf(subTaskAttributes.get("segmentId").toString());
        boolean endFlag = (boolean) subTaskAttributes.get("endFlag");

        boolean sendFlag = this.receiveShard(taskId, jobId, callId, segmentId, endFlag);
        if(sendFlag){
            log.info("TaskId:{}, jobId:{}, callId:{}, segmentId:{}, endFlag:{} send shard success",
                    taskId, jobId, callId, segmentId, endFlag);
            xmlEventPublisher.mqEventPublish(MqEvent.SHARD_REND_RESP, task, subTask);
        }

    }

    public void finishShard(MqEntityEvent event){
        SubTask subTask = event.getSubTask();
        Task task = event.getTask();

        String taskId = String.valueOf(task.getId());
        String jobId = String.valueOf(subTask.getId());

        Map<String, Object> subTaskAttributes = subTask.getAttributes();
        int segmentId = Integer.valueOf(subTaskAttributes.get("segmentId").toString());

        String callId = subTask.getDataId();
        SubTask nextSubTask = this.finishShardAndNext(taskId, jobId, callId, segmentId);

        // 没有下一分片
        if(nextSubTask == null){
            log.info("TaskId:{}, jobId:{}, callId:{}, segmentId:{} finish success, not next segmentId",
                    taskId, jobId, callId, segmentId);
        }else{
            log.info("TaskId:{}, jobId:{}, callId:{}, segmentId:{}, finish success, nextSegmentId:{} send next shard success",
                    taskId, jobId, callId, segmentId, segmentId+1);
            xmlEventPublisher.mqEventPublish(MqEvent.SHARD_REND_RESP, task, nextSubTask);
        }
    }

    public void updateAsrText(MqEntityEvent event) {
        SubTask subTask = event.getSubTask();
        Task task = event.getTask();

        String taskId = String.valueOf(task.getId());
        String jobId = String.valueOf(subTask.getId());
        String callId = subTask.getDataId();
        boolean isStream = task.isStream();
        if(!isStream){
            log.info("TaskId:{}, jobId:{}, callId:{} is not stream, break.",
                    taskId, jobId, callId);
            return;
        }
        ShardTask shardTask = shardTaskDao.getShard(callId);
        if(shardTask == null){
            log.info("TaskId:{}, jobId:{}, callId:{} is null, update asr text",
                    taskId, jobId, callId);
            return;
        }

        Map<String, Object> subTaskAttributes = subTask.getAttributes();
        int segmentId = Integer.valueOf(subTaskAttributes.get("segmentId").toString());

        String segmentAsrText = (String)subTask.getResponseResults().get(Attribute.ASR_TEXT.getName());
        Long segmentAsrDuration = (Long) subTask.getResponseResults().get(Attribute.ASR_DURATION.getName());

        String shardAsr = StringUtils.hasLength(shardTask.getAsrText())?shardTask.getAsrText():"";
        Long shardDuration = shardTask.getAsrDuration();

        String appendAsrText = shardAsr + segmentAsrText;
        Long appendDuration = shardDuration + segmentAsrDuration;

        shardTaskDao.shardAsrText(callId, segmentId, segmentAsrText, appendAsrText, appendDuration);
        subTask = asrService.updateSubTaskAsrText(subTask, appendAsrText, appendDuration);

        event.setSubTask(subTask);
        log.info("TaskId:{}, jobId:{}, callId:{}, segmentId:{} update asr text success",
                taskId, jobId, callId, segmentId);
    }

    /**
     * 收到分片
     */
    public boolean receiveShard(String taskId, Long jobId, String callId, int segmentId, boolean endFlag){

        log.info("TaskId:{}, jobId:{}, callId:{}, segmentId:{}, endFlag:{} step1 saveShard",
                taskId, jobId, callId, segmentId, endFlag);
        // 1. 分片先入库
        shardTaskDao.saveShard(jobId, callId,segmentId,endFlag);

        log.info("TaskId:{}, jobId:{}, callId:{}, segmentId:{}, endFlag:{} step2 preShardFinished",
                taskId, jobId, callId, segmentId, endFlag);
        // 2. 判断前一个是否完成
        boolean preFinished = shardTaskDao.preShardFinished(callId, segmentId);

        // 前一个未完成
        if(!preFinished){
            log.info("TaskId:{}, jobId:{}, callId:{}, segmentId:{}, endFlag:{} pre-shard not finish, waiting...",
                    taskId, jobId, callId, segmentId, endFlag);
            return false;
        }

        log.info("TaskId:{}, jobId:{}, callId:{}, segmentId:{}, endFlag:{} step3 preShardFinished end",
                taskId, jobId, callId, segmentId, endFlag);

//        log.info("TaskId:{}, jobId:{}, callId:{}, segmentId:{}, endFlag:{} step3 tryMarkProcess",
//                taskId, jobId, callId, segmentId, endFlag);
        // 3. CAS抢发送权
//        boolean success = shardTaskDao.tryMarkProcess(callId, segmentId);
//
//        log.info("TaskId:{}, jobId:{}, callId:{}, segmentId:{}, endFlag:{} step4 tryMarkProcess end ",
//                taskId, jobId, callId, segmentId, endFlag);
//
//        if(!success){
//            log.info("TaskId:{}, jobId:{}, callId:{}, segmentId:{}, endFlag:{} try process fail, waiting...",
//                    taskId, jobId, callId, segmentId, endFlag);
//            return false;
//        }

        return true;
    }

    /**
     * 完成分片
     */
    public SubTask finishShardAndNext(String taskId, String jobId, String callId, int segmentId){
        // 1. 更新完成状态
        shardTaskDao.finishShard(callId, segmentId);

        // 2. 推进下一分片
        int nextSegmentId = segmentId + 1;

        ShardTask.SharkInfo nextShard = shardTaskDao.getShardInfo(callId, nextSegmentId);

        // 下一分片不存在
        if(nextShard == null){
            log.info("TaskId:{}, jobId:{}, callId:{}, segmentId:{} next shard not exist",
                    taskId, jobId, callId, segmentId);
            return null;
        }

//        // 已发送过
//        if(Boolean.TRUE.equals(nextShard.isProcess())){
//            log.info("TaskId:{}, jobId:{}, callId:{}, nextSegment:{} already process",
//                    taskId, jobId, callId, segmentId);
//            return null;
//        }
//
//        // 3. CAS抢发送权
//        boolean success =
//                shardTaskDao.tryMarkProcess(
//                        callId,
//                        nextSegmentId);
//
//        if(!success){
//            log.info("TaskId:{}, jobId:{}, callId:{}, nextSegment:{} try process fail",
//                    taskId, jobId, callId, segmentId);
//            return null;
//        }

        // 4. 查询nextSubTask
        Long nextSubTaskId = nextShard.getSubTaskId();

        SubTask nextSubTask = subTaskService.getSubTaskByJobId(String.valueOf(nextSubTaskId));

        if(nextSubTask == null){
            log.error(
                    "TaskId:{}, jobId:{}, callId:{}, nextSubTaskId:{} not exist",
                    taskId, jobId, callId,nextSubTaskId);
            return null;
        }

        return nextSubTask;
    }


}
