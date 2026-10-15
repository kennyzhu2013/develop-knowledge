package com.cmic.dao;

import com.cmic.pojo.entity.ModelMeta;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface IModelMetaDao extends MongoRepository<ModelMeta, ObjectId> {
    ModelMeta findByModel(String model);
    ModelMeta findByModelAndVersion(String model, String version);
    ModelMeta findByModelAndIsDefaultTrue(String model);
    List<ModelMeta> findAllByModel(String model);
}
