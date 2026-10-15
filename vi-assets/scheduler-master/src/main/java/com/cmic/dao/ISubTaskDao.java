package com.cmic.dao;

import com.cmic.pojo.entity.SubTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ISubTaskDao extends MongoRepository<SubTask, Long> {
    Page<SubTask> findByStatus(int status, Pageable pageable);

    List<SubTask> findByTaskId(long taskId);

    @Aggregation(pipeline = {
            "{ '$match': { 'callback': 1 , 'taskId': ?0} }",
            "{ '$count': 'count' }"
    })
    int countByCallbackAndTaskId(long taskId);

    @Aggregation(pipeline = {
            "{ '$match': { 'callback': 0 , 'taskId': ?0} }",
            "{ '$count': 'count' }"
    })
    int countByNotCallbackAndTaskId(long taskId);

    SubTask findFirstByTaskIdAndCallbackOrderByUpdateTimeDesc(long taskId, int callback);

    void deleteByTaskId(long taskId);
}
