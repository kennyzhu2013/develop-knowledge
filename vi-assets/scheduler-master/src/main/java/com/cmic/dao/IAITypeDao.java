package com.cmic.dao;

import com.cmic.pojo.entity.AIType;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface IAITypeDao extends MongoRepository<AIType, Long> {
    AIType findByName(String name);
}
