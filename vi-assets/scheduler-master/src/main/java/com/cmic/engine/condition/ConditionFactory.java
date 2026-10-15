package com.cmic.engine.condition;

import com.cmic.config.xml.IfElseXml;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ConditionFactory {
    @Autowired
    private CalculateCondition condition;
    @Autowired
    private TaskCondition taskCondition;
    @Autowired
    private RedisCondition redisCondition;

    public Condition getCondition(IfElseXml ifElseXml){
        String application = ifElseXml.getApplication();
        switch (application){
            case "taskCondition":
                return taskCondition;
            case "redisCondition":
                return redisCondition;
            default:
                return condition;

        }
    }
}
