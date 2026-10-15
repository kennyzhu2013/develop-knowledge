package com.cmic.dao;

import com.cmic.pojo.entity.Task;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Slf4j
@Repository
public class TaskDaoImpl {
    @Autowired
    private ITaskDao iTaskDao;

    @Autowired
    private MongoTemplate mongoTemplate;

    public Task findByRequestId(String requestId){
        return iTaskDao.findByRequestId(requestId);
    }

    public List<Task> findByNeedTaskCallbackAndCallbackAndCreateTimeBeforeOrderByPriorityDesc(boolean needTaskCallback, int callback, LocalDateTime createTime){
        return iTaskDao.findByNeedTaskCallbackAndCallbackAndCreateTimeBeforeOrderByPriorityDesc(needTaskCallback, callback, createTime);
    }

    public List<Task> findByNeedTaskCallbackAndCallbackAndCreateTimeBetween(boolean needTaskCallback, int callback, LocalDateTime minTime, LocalDateTime maxTime){
        return iTaskDao.findByNeedTaskCallbackAndCallbackAndCreateTimeBetween(needTaskCallback, callback, minTime, maxTime);
    }

    public List<Task> findByNeedTaskCallbackAndCallbackAndStreamAndCreateTimeBetween(boolean needTaskCallback, int callback, boolean stream, LocalDateTime minTime, LocalDateTime maxTime){
        return iTaskDao.findByNeedTaskCallbackAndCallbackAndStreamAndCreateTimeBetween(needTaskCallback, callback, stream, minTime, maxTime);
    }

    public Map<String, Integer> countTaskByProcessId(boolean needTaskCallback, int callback, boolean stream, LocalDateTime minTime, LocalDateTime maxTime) {
        Criteria criteria = Criteria.where("needTaskCallback").is(needTaskCallback)
                .and("callback").is(callback)
                .and("stream").is(true)
                .and("createTime").gte(minTime).lte(maxTime);
        Aggregation aggregation = newAggregation(
                match(criteria),
                project("processId"),
                group("processId").count().as("count")
        );
        AggregationResults<Map> results = mongoTemplate.aggregate(aggregation, "task", Map.class);

        Map<String, Integer> result = new HashMap<>();
        for (Map<String, Object> record : results.getMappedResults()) {
            String processId = (String) record.get("_id");
            Number countObj = (Number) record.get("count");

            if (processId != null && countObj != null) {
                result.put(processId, countObj.intValue());
            }
        }
        return result;
    }

    public void updateStatus(long taskId, int status){
        Criteria criteria = Criteria.where("id").is(taskId);

        // 创建更新操作
        Update update = new Update();
        update.set("status", status);
        update.set("updateTime", LocalDateTime.now());

        // 执行更新操作
        mongoTemplate.updateFirst(new Query(criteria), update, Task.class);
    }

    public void updateTaskFinish(long taskId, int finish){
        Criteria criteria = Criteria.where("id").is(taskId);

        // 创建更新操作
        Update update = new Update();
        update.set("finish", finish);
        update.set("updateTime", LocalDateTime.now());

        // 执行更新操作
        mongoTemplate.updateFirst(new Query(criteria), update, Task.class);
    }

    public void updateCallback(long taskId, int callback){
        Criteria criteria = Criteria.where("id").is(taskId);

        // 创建更新操作
        Update update = new Update();
        update.set("callback", callback);
        update.set("updateTime", LocalDateTime.now());

        // 执行更新操作
        mongoTemplate.updateFirst(new Query(criteria), update, Task.class);
    }

    public int getTaskCallback(long taskId) {
        Query query = new Query(Criteria.where("_id").is(taskId));
        query.fields().include("callback").exclude("_id"); // 仅获取 callback 字段

        Task task = mongoTemplate.findOne(query, Task.class);
        return task != null ? task.getCallback() : 0;
    }

    public void save(Task task){
        iTaskDao.save(task);
    }

    public Task getTaskById(long taskId){
        Optional<Task> result = iTaskDao.findById(taskId);
        if(result.isEmpty()){
            return null;
        }
        return result.get();
    }

    public Task getTaskByTransactionId(long transactionId){
        return iTaskDao.findByTransactionId(transactionId);
    }
}
