package com.cmic.dao;

import com.cmic.pojo.entity.ShardTask;
import com.mongodb.client.result.UpdateResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Slf4j
@Repository
public class ShardTaskDaoImpl {
    @Autowired
    private MongoTemplate mongoTemplate;


    /**
     * 分片到达
     */
    public void saveShard(Long subTaskId,
                          String callId,
                          int segmentId,
                          boolean endFlag){

        String key = "sharkInfoMap." + segmentId;

        ShardTask.SharkInfo sharkInfo = new ShardTask.SharkInfo();

        sharkInfo.setSegmentId(segmentId);
        sharkInfo.setSubTaskId(subTaskId);
        sharkInfo.setEndFlag(endFlag);
        sharkInfo.setReceived(true);
//        sharkInfo.setProcess(false);
        sharkInfo.setFinish(false);

        Query query = Query.query(
                Criteria.where("callId")
                        .is(callId)
        );

        Update update = new Update()
                .setOnInsert("callId", callId)
                .set(key, sharkInfo);

        mongoTemplate.upsert(
                query,
                update,
                ShardTask.class);
    }

    /**
     * 前一个分片是否已完成
     */
    public boolean preShardFinished(String callId,
                                    int segmentId){

        if(segmentId == 0){
            return true;
        }

        String key =
                "sharkInfoMap."
                        + (segmentId - 1)
                        + ".finish";

        Query query = Query.query(
                Criteria.where("callId")
                        .is(callId)
                        .and(key)
                        .is(true)
        );

        return mongoTemplate.exists(
                query,
                ShardTask.class);
    }

    /**
     * CAS更新process
     * 防止重复处理
     */
//    public boolean tryMarkProcess(String callId,
//                                 int segmentId){
//
//        String key =
//                "sharkInfoMap."
//                        + segmentId
//                        + ".process";
//
//        Query query = Query.query(
//                Criteria.where("callId")
//                        .is(callId)
//                        .and(key)
//                        .is(false)
//        );
//
//        Update update = new Update()
//                .set(key, true);
//
//        UpdateResult result =
//                mongoTemplate.updateFirst(
//                        query,
//                        update,
//                        ShardTask.class);
//
//        return result.getModifiedCount() == 1;
//    }

    /**
     * 更新asr
     */
    public void shardAsrText(String callId,
                            int segmentId,
                            String shardAsrText, String appendAsrText, Long appendDuration){

        String prefix =
                "sharkInfoMap." + segmentId;

        Query query = Query.query(
                Criteria.where("callId")
                        .is(callId)
        );

        Update update = new Update()
                .set(prefix + ".asr", shardAsrText)
                .set("asrText", appendAsrText)
                        .set("asrDuration", appendDuration);

        mongoTemplate.updateFirst(
                query,
                update,
                ShardTask.class);
    }

    /**
     * 完成分片
     */
    public void finishShard(String callId,
                            int segmentId){

        String prefix =
                "sharkInfoMap." + segmentId;

        Query query = Query.query(
                Criteria.where("callId")
                        .is(callId)
        );

        Update update = new Update()
                .set(prefix + ".finish", true);

        mongoTemplate.updateFirst(
                query,
                update,
                ShardTask.class);
    }

    /**
     * 获取某个分片
     */
    public ShardTask.SharkInfo getShardInfo(String callId,
                                            Integer segmentId){

        Query query = Query.query(
                Criteria.where("callId")
                        .is(callId)
        );

        query.fields()
                .include(
                        "sharkInfoMap." + segmentId);

        ShardTask shardTask =
                mongoTemplate.findOne(
                        query,
                        ShardTask.class);

        if(shardTask == null
                || shardTask.getSharkInfoMap() == null){

            return null;
        }

        return shardTask.getSharkInfoMap()
                .get(segmentId);
    }

    public void appendAsrText(String callId,
                              String asr){

        Query query = Query.query(
                Criteria.where("callId")
                        .is(callId)
        );

        ShardTask shardTask =
                mongoTemplate.findOne(
                        query,
                        ShardTask.class);

        if(shardTask == null){
            return;
        }

        String oldAsr =
                Optional.ofNullable(
                                shardTask.getAsrText())
                        .orElse("");

        Update update = new Update()
                .set("asrText", oldAsr + asr);

        mongoTemplate.updateFirst(
                query,
                update,
                ShardTask.class);
    }

    /**
     * 下一个分片是否已收到
     */
    public boolean nextShardReceived(String callId,
                                     int nextSegmentId){

        String key =
                "sharkInfoMap."
                        + nextSegmentId
                        + ".received";

        Query query = Query.query(
                Criteria.where("callId")
                        .is(callId)
                        .and(key)
                        .is(true)
        );

        return mongoTemplate.exists(
                query,
                ShardTask.class);
    }

    public ShardTask getShard(String callId){
        Query query = Query.query(
                Criteria.where("callId")
                        .is(callId)
        );

        return mongoTemplate.findOne(
                        query,
                        ShardTask.class);
    }
}
