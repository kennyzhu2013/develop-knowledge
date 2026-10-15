package com.cmic.dao;

import com.cmic.pojo.entity.Process;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface IProcessDao extends MongoRepository<Process, Long> {
    Process findByProcessIdAndStatus(String processId, int status);
    Process findByProcessId(String processId);

    Process findByRequestId(String requestId);

    Process findByXmlFileName(String xmlFileName);

    List<Process> findAllByStatus(int status);
}
