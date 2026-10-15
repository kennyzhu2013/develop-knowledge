package com.cmic.engine.action;

import com.cmic.config.xml.ActionXml;
import com.cmic.engine.event.EventOrigin;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.service.task.ShardTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ShardAction  implements Action{
    @Autowired
    private ShardTaskService shardTaskService;
    @Override
    public void validate(ActionXml obj) throws Exception {

    }

    @Override
    public void apply(ActionXml actionXml, EventOrigin eventOrigin, Object... obj) throws Exception {
        String op = actionXml.getOp();
        MqEntityEvent event = (MqEntityEvent) obj[0];
        if(op.equalsIgnoreCase("sendShard")){
            shardTaskService.sendShard(event);
        } else if (op.equalsIgnoreCase("finishShard")) {
            shardTaskService.finishShard(event);
        } else if (op.equalsIgnoreCase("updateAsrText")) {
            shardTaskService.updateAsrText(event);
        }
    }


}
