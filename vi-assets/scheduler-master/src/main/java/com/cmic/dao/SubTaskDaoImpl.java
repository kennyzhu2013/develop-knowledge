package com.cmic.dao;

import com.cmic.pojo.dto.CallbackModelRequest;
import com.cmic.pojo.entity.SubTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
@Slf4j
@Repository
public class SubTaskDaoImpl {
    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private ISubTaskDao iSubTaskDao;

    public void save(SubTask subTask){
        mongoTemplate.save(subTask);
    }

    public void saveAll(List<SubTask> list){
        mongoTemplate.insertAll(list);
    }

    public SubTask findById(long jobId){
        return mongoTemplate.findById(jobId, SubTask.class);
    }

    public void deleteByTaskId(long taskId){
        Query query = new Query(Criteria.where("taskId").is(taskId));
        mongoTemplate.remove(query, SubTask.class);
    }

    public List<SubTask> findAllByTaskId(long taskId){
        return iSubTaskDao.findByTaskId(taskId);
    }

    public int countByCallbackAndTaskId(long taskId){
        return iSubTaskDao.countByCallbackAndTaskId(taskId);
    }

    public int countByNotCallbackAndTaskId(long taskId){
        return iSubTaskDao.countByNotCallbackAndTaskId(taskId);
    }

    public SubTask findByTaskIdAndCallbackOrderByUpdateTimeDesc(long taskId){
        return iSubTaskDao.findFirstByTaskIdAndCallbackOrderByUpdateTimeDesc(taskId, 1);
    }

    public void updateSubTaskFinish(long jobId, int finish){
        // 创建查询条件
        Criteria criteria = Criteria.where("id").is(jobId);

        // 创建更新操作
        Update update = new Update();
        update.set("finish", finish);
        update.set("updateTime", LocalDateTime.now());

        // 执行更新操作
        mongoTemplate.updateFirst(new Query(criteria), update, SubTask.class);
    }

    public void updateSubTaskCallback(long jobId, int callback){
        Criteria criteria = Criteria.where("id").is(jobId);

        // 创建更新操作
        Update update = new Update();
        update.set("callback", callback);
        update.set("updateTime", LocalDateTime.now());

        // 执行更新操作
        mongoTemplate.updateFirst(new Query(criteria), update, SubTask.class);
    }

    public void updateSubTaskItem(long jobId, Map<String, Object> params){
        Criteria criteria = Criteria.where("id").is(jobId);

        // 创建更新操作
        Update update = new Update();

        for(String key:params.keySet()){
            update.set(key, params.get(key));
        }

        update.set("updateTime", LocalDateTime.now());

        // 执行更新操作
        mongoTemplate.updateFirst(new Query(criteria), update, SubTask.class);
    }

    public void updateSubTaskResponseResult(String jobId, Map<String, Object> response){
        Criteria criteria = Criteria.where("_id").is(Long.valueOf(jobId));

        // 创建更新操作
        Update update = new Update();
        update.set("responseResults", response);
        update.set("updateTime", LocalDateTime.now());

        // 执行更新操作
        mongoTemplate.updateFirst(new Query(criteria), update, SubTask.class);
    }

    public SubTask updateSubTaskResponseResult(long jobId, String key, Object value){
        Criteria criteria = Criteria.where("_id").is(jobId);

        // 创建更新操作
        Update update = new Update();
        update.set("responseResults."+key, value);
        update.set("updateTime", LocalDateTime.now());

        // 执行更新操作
//        mongoTemplate.updateFirst(new Query(criteria), update, SubTask.class);

        return mongoTemplate.findAndModify(
                new Query(criteria),
                update,
                FindAndModifyOptions.options().returnNew(true),
                SubTask.class);
    }

    public void updateSubTaskResponseMap(long jobId, Map<String, Object> results){
        Criteria criteria = Criteria.where("_id").is(jobId);
        Update update = new Update();

        for(String key:results.keySet()){
            update.set("responseResults."+key, results.get(key));
        }
        update.set("updateTime", LocalDateTime.now());

        // 执行更新操作
        mongoTemplate.updateFirst(new Query(criteria), update, SubTask.class);
    }

    public void updateSubTaskErrorMessage(String jobId, String errorMessage){
        Criteria criteria = Criteria.where("id").is(Long.valueOf(jobId));

        // 创建更新操作
        Update update = new Update();
        update.set("errorMessage", errorMessage);
        update.set("updateTime", LocalDateTime.now());

        // 执行更新操作
        mongoTemplate.updateFirst(new Query(criteria), update, SubTask.class);
    }

    public void removeSubTaskAsr(long jobId){
        Criteria criteria = Criteria.where("id").is(jobId);

        // 创建更新操作
        Update update = new Update();
        update.unset("responseResults.asrText");
        update.unset("responseResults.asrInfo");
        update.unset("responseResults.cqAgentDetail");
        update.set("updateTime", LocalDateTime.now());

        // 执行更新操作
        mongoTemplate.updateFirst(new Query(criteria), update, SubTask.class);
    }

    public void updateSubTaskStatus(long jobId, int status){
        Criteria criteria = Criteria.where("id").is(jobId);

        // 创建更新操作
        Update update = new Update();
        update.set("status", status);
        update.set("updateTime", LocalDateTime.now());

        // 执行更新操作
        mongoTemplate.updateFirst(new Query(criteria), update, SubTask.class);
    }

    public void updateSubTaskResponseMap(long jobId, String model, Map<String, Object> results){
        Criteria criteria = Criteria.where("_id").is(jobId);
        Update update = new Update();

        for(String key:results.keySet()){
            update.set("responseResults."+model+"."+key, results.get(key));
        }
        update.set("updateTime", LocalDateTime.now());

        // 执行更新操作
        mongoTemplate.updateFirst(new Query(criteria), update, SubTask.class);
    }

    public void updateSubTaskResponseModel(long jobId, String model){
        Criteria criteria = Criteria.where("_id").is(jobId);
        Update update = new Update();

        update.set("responseResults."+model, model);
        update.set("updateTime", LocalDateTime.now());

        // 执行更新操作
        mongoTemplate.updateFirst(new Query(criteria), update, SubTask.class);
    }

    public SubTask updateSubTaskModelAttribute(long jobId, String model, Map<String, Object> results){
        model = model.toLowerCase();
        Criteria criteria = Criteria.where("_id").is(jobId);
        Update update = new Update();
        if(!CollectionUtils.isEmpty(results)){
            for(String key:results.keySet()){
                update.set("responseResults."+model+"."+key, results.get(key));
            }
        }
        update.set("updateTime", LocalDateTime.now());
        // 执行更新操作
        SubTask subTask = mongoTemplate.findAndModify(
                new Query(criteria),
                update,
                FindAndModifyOptions.options().returnNew(true),
                SubTask.class);
        return subTask;
    }

    public SubTask updateSubTaskModel(long jobId, CallbackModelRequest request, Map<String, Object> results){
        long startTime = System.currentTimeMillis();
        String model = request.getName().toLowerCase();
        if(!StringUtils.hasLength(model)){
            return null;
        }
        Criteria criteria = Criteria.where("_id").is(jobId);
        Update update = new Update();

        if(StringUtils.hasLength(request.getCode())){
            update.set("responseResults."+model+".code", request.getCode());
        }
        if(StringUtils.hasLength(request.getMessage())){
            update.set("responseResults."+model+".message", request.getMessage());
        }
        if(request.getStatus() != null){
            update.set("responseResults."+model+".status", request.getStatus());
        }
        if(request.getStartTime() != null){
            update.set("responseResults."+model+".startTime", request.getStartTime());
        }
        if(request.getEndTime() != null){
            update.set("responseResults."+model+".endTime", request.getEndTime());
        }
        if(request.getModelQcResult() != null){
            update.set("responseResults."+model+".modelQcResult", request.getModelQcResult());
        }
        if(request.getRiskLevel() != null){
            update.set("responseResults."+model+".riskLevel", request.getRiskLevel());
        }

        if(!CollectionUtils.isEmpty(results)){
            for(String key:results.keySet()){
                update.set("responseResults."+key, results.get(key));
            }
        }

        update.set("updateTime", LocalDateTime.now());

        // 执行更新操作
        SubTask subTask = mongoTemplate.findAndModify(
                new Query(criteria),
                update,
                FindAndModifyOptions.options().returnNew(true),
                SubTask.class);
        long costTime = System.currentTimeMillis() - startTime;
        log.info("jobId:{}, model:{}, response update db cost time:{}ms", jobId, model, costTime);
        return subTask;
    }
}
