package com.cmic.service.ai;

import com.cmic.engine.event.MqEntityEvent;
import com.cmic.pojo.dto.BaseResponse;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;

public interface IMqService {
    void sendToMq(MqEntityEvent event) throws Exception;

    void handlerMessage(Task task, SubTask subTask, BaseResponse<?> baseResponse);
}
