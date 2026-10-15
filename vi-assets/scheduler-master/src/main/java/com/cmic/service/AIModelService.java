package com.cmic.service;

import com.cmic.dao.AIModelDaoImpl;
import com.cmic.dao.IAIModelDao;
import com.cmic.pojo.dto.AiModelPageResponse;
import com.cmic.pojo.entity.AIModel;
import com.cmic.pojo.entity.AIType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AIModelService{
    @Autowired
    private IAIModelDao iaiModelDao;
    @Autowired
    private AIModelDaoImpl aiModelDao;

    public void save(AIModel aiModel){
        iaiModelDao.save(aiModel);
    }

    public AIModel queryByModel(String model){
        return iaiModelDao.findByModel(model);
    }

    public AiModelPageResponse findByCriteria(String model, AIType aiType, String desc, int page, int size){
        return aiModelDao.findByCriteria(model, aiType, desc, page, size);
    }

    public void delete(AIModel aiModel){
        iaiModelDao.delete(aiModel);
    }
}
