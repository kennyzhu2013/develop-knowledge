package com.cmic.engine.condition;

import com.cmic.config.xml.IfElseXml;
import com.cmic.engine.event.EventOrigin;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component("taskCondition")
public class TaskCondition  implements Condition{
    @Override
    public boolean validate(IfElseXml ifElseXml) {
        return false;
    }

    @Override
    public boolean match(IfElseXml ifElseXml, EventOrigin eventOrigin, Object... obj) {
        ExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext context = new StandardEvaluationContext();
        String expression = ifElseXml.getExpression();

        MqEntityEvent mqEvent = (MqEntityEvent) obj[0];
        Task task = mqEvent.getTask();
        SubTask subTask = mqEvent.getSubTask();
        String taskId = String.valueOf(task.getId());
        String jobId = String.valueOf(subTask.getId());

        context.setVariable("task", task);
        context.setVariable("job", subTask);

        boolean result = parser.parseExpression(expression).getValue(context, Boolean.class);
        log.info("TaskId:{}, jobId:{}, expression:{}, result:{}", taskId, jobId, ifElseXml.getExpression(), result);
        return result;
    }

    public static void main(String[] args) {

    }
}
