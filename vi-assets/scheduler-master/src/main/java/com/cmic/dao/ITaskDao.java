package com.cmic.dao;

import com.cmic.pojo.entity.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ITaskDao extends MongoRepository<Task, Long> {
    Task findFirstByStatusOrderByPriorityDescCreateTimeDesc(int status);

    Page<Task> findByStatusOrderByPriorityDescCreateTimeDesc(int status, Pageable pageable);

    Task findByRequestId(String requestId);

    List<Task> findByNeedTaskCallbackAndCallbackAndCreateTimeBeforeOrderByPriorityDesc(boolean needTaskCallback, int callback, LocalDateTime createTime);
    List<Task> findByNeedTaskCallbackAndCallbackAndCreateTimeBetween(boolean needTaskCallback, int callback, LocalDateTime minTime, LocalDateTime maxTime);

    Task findByTransactionId(long transactionId);

    List<Task> findByNeedTaskCallbackAndCallbackAndStreamAndCreateTimeBetween(boolean needTaskCallback, int callback, boolean stream, LocalDateTime minTime, LocalDateTime maxTime);
}
