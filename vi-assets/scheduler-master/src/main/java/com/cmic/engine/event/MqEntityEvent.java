package com.cmic.engine.event;

import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import org.springframework.context.ApplicationEvent;

public class MqEntityEvent extends ApplicationEvent {
    public MqEntityEvent(Object source) {
        super(source);
    }

    private Task task;

    private SubTask subTask;

    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        this.task = task;
    }

    public SubTask getSubTask() {
        return subTask;
    }

    public void setSubTask(SubTask subTask) {
        this.subTask = subTask;
    }
}
