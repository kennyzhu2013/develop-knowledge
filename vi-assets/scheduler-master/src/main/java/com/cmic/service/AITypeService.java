package com.cmic.service;

import com.cmic.dao.IAITypeDao;
import com.cmic.pojo.dto.AiTypeRequest;
import com.cmic.pojo.entity.AIType;
import com.cmic.util.SnowflakeUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class AITypeService {
    @Autowired
    private IAITypeDao iaiTypeDao;

    public void save(AiTypeRequest request){
        AIType aiType = AIType.builder()
                .id(SnowflakeUtils.generateId())
                .name(request.getName())
                .nickName(request.getNickName())
                .comment(request.getComment())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        iaiTypeDao.save(aiType);
    }

    public AIType findById(Long id){
        Optional<AIType> result = iaiTypeDao.findById(id);
        if(result.isEmpty()){
            return null;
        }
        return result.get();
    }

    public AIType findByName(String name){
        return iaiTypeDao.findByName(name);
    }

    public void save(AIType aiType){
        iaiTypeDao.save(aiType);
    }

    public List<AIType> findAll(){
        return iaiTypeDao.findAll();
    }

    public void delete(AIType aiType){
        iaiTypeDao.delete(aiType);
    }
}
