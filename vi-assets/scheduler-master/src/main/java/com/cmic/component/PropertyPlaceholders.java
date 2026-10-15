package com.cmic.component;

import com.cmic.engine.event.EventOrigin;
import com.cmic.engine.event.HttpEvent;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class PropertyPlaceholders {

    public String resolve(String data, EventOrigin eventOrigin, Map<String, Object> parameterMap, Object... objs){
        Pattern pattern = Pattern.compile("\\$\\{(.+?)}");
        Matcher matcher = pattern.matcher(data);

        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String old = matcher.group(0);
            String attribute = matcher.group(1);
            Object value = this.match(attribute, eventOrigin, objs);

            parameterMap.put(attribute, value);
            // 执行替换
            matcher.appendReplacement(result, "#"+attribute);
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private Object match(String attribute, EventOrigin eventOrigin, Object... objs){
        switch (eventOrigin){
            case MQ:
                return getValue(attribute, objs);
            case HTTP:
                return getHttpValue(attribute, objs);
        }
        return null;
    }

    private Object getValue(String attribute, Object... objs){
        MqEntityEvent mqEvent = (MqEntityEvent) objs[0];
        Task task = mqEvent.getTask();
        SubTask subTask = mqEvent.getSubTask();
        if(attribute.contains(".")){
            String scope = attribute.split("\\.")[0];
            String key;
            Object value = null;
            switch (scope){
                case "job":
                    key = attribute.split("\\.")[1];
                    value = subTask.getResponseResults().get(key);
                    break;
                case "task":
                    Map<String, Object> attributes = task.getAttributes();
                    key = attribute.split("\\.")[1];
                    value = attributes.get(key);
                    break;
            }
            return value;
        }
        return subTask.getResponseResults().get(attribute);
    }

    private Object getHttpValue(String attribute, Object... objs){
        HttpEvent event = (HttpEvent) objs[0];
        String processId = event.getProcessId();

        //TODO
        return null;
    }
}
