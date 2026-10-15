package com.cmic.dao;

import com.cmic.pojo.entity.HisProcess;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface IHisProcessDao extends MongoRepository<HisProcess, Long> {
    HisProcess findByProcessId(String processId);
    HisProcess findByProcessIdAndEndTimeIsNull(String processId);
    long countByProcessId(String processId);
}
