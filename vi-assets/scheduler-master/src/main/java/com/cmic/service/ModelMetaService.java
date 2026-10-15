package com.cmic.service;

import com.cmic.dao.IModelMetaDao;
import com.cmic.pojo.entity.ModelMeta;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Slf4j
@Service
public class ModelMetaService {

    @Autowired
    private IModelMetaDao iModelMetaDao;

    public ModelMeta queryByModel(String model) {
        return iModelMetaDao.findByModel(model);
    }

    public ModelMeta save(ModelMeta modelMeta) {
        if (Boolean.TRUE.equals(modelMeta.getIsDefault())) {
            // 将同 model 下其他默认记录置为非默认
            ModelMeta currentDefault = iModelMetaDao.findByModelAndIsDefaultTrue(modelMeta.getModel());
            if (currentDefault != null && !Objects.equals(currentDefault.getId(), modelMeta.getId())) {
                currentDefault.setIsDefault(false);
                iModelMetaDao.save(currentDefault);
            }
        }
        if (modelMeta.getIsDefault() == null) {
            modelMeta.setIsDefault(false);
        }
        return iModelMetaDao.save(modelMeta);
    }

    public ModelMeta queryByModelAndVersion(String model, String version) {
        return iModelMetaDao.findByModelAndVersion(model, version);
    }

    /** 查询某 model 的默认版本，未指定 version 时使用 */
    public ModelMeta queryDefaultByModel(String model) {
        return iModelMetaDao.findByModelAndIsDefaultTrue(model);
    }
}
