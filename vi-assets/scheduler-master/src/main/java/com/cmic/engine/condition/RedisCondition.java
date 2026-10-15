package com.cmic.engine.condition;

import com.cmic.config.xml.IfElseXml;
import com.cmic.engine.event.EventOrigin;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import com.cmic.service.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component("redisCondition")
public class RedisCondition implements Condition{
    @Autowired
    private RedisService redisService;

    private static final Pattern PATTERN =
            Pattern.compile("#(\\w+)\\.(\\w+)");
    @Override
    public boolean validate(IfElseXml ifElseXml) {
        return false;
    }

    @Override
    public boolean match(IfElseXml ifElseXml, EventOrigin eventOrigin, Object... obj) {
        MqEntityEvent mqEvent = (MqEntityEvent) obj[0];
        Task task = mqEvent.getTask();
        SubTask subTask = mqEvent.getSubTask();
        String taskId = String.valueOf(task.getId());
        String jobId = String.valueOf(subTask.getId());

        ExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext context = new StandardEvaluationContext();
        String expression = ifElseXml.getExpression();

        Matcher matcher = PATTERN.matcher(expression);
        while (matcher.find()) {
            String prefix = matcher.group(1);
            String field = matcher.group(2);
            String variableName = prefix + "_" + field;
            Object value = null;
            switch (prefix) {
                case "call":
                    value = redisService.getAttributeCallIdItem(subTask.getDataId(), field);
                    break;
                case "subTask":
//                    value = redisService.getAttributeTransaction(task.getTransactionId(), field);
                    break;
            }

            context.setVariable(variableName, value);

            expression = expression.replace(
                    "#" + prefix + "." + field,
                    "#" + variableName
            );
        }

        boolean result = parser.parseExpression(expression).getValue(context, Boolean.class);
        log.info("TaskId:{}, jobId:{}, expression:{}, result:{}", taskId, jobId, ifElseXml.getExpression(), result);
        return result;
    }

    public static void main(String[] args) {

        ExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext context = new StandardEvaluationContext();
        String expression = "#transaction['status'] == 'SUCCESS'";

        Map<Object, Object> transactionMap = new HashMap<>();
        transactionMap.put("status", "SUCCESS");

        context.setVariable("transaction", transactionMap);

        boolean result = parser.parseExpression(expression).getValue(context, Boolean.class);
        log.info("result:{}", result);
    }
}
