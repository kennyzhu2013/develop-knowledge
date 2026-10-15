package com.cmic.engine.condition;

import com.cmic.component.PropertyPlaceholders;
import com.cmic.config.xml.IfElseXml;
import com.cmic.engine.event.EventOrigin;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.pojo.dto.ai.KeywordAnalysisResponse;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import com.cmic.util.CommonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.util.*;

@Slf4j
@Component("calculate")
public class CalculateCondition implements Condition{
    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private PropertyPlaceholders propertyPlaceholders;

    @Override
    public boolean validate(IfElseXml ifElseXml) {
        return false;
    }

    @Override
    public boolean match(IfElseXml ifElseXml, EventOrigin eventOrigin, Object... obj) {
        ExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("calculate", applicationContext.getBean("calculate"));

        Map<String, Object> parameterMap = new HashMap<>();
        String expression = propertyPlaceholders.resolve(ifElseXml.getExpression(), eventOrigin, parameterMap, obj);
        for(String key: parameterMap.keySet()){
            context.setVariable(key, parameterMap.get(key));
        }

        boolean result = (boolean) parser.parseExpression(expression).getValue(context);
        // 输出结果
        MqEntityEvent mqEvent = (MqEntityEvent) obj[0];
        Task task = mqEvent.getTask();
        SubTask subTask = mqEvent.getSubTask();
        String taskId = String.valueOf(task.getId());
        String jobId = String.valueOf(subTask.getId());
        log.info("TaskId:{}, jobId:{}, expression:{}, result:{}", taskId, jobId, ifElseXml.getExpression(), result);
        return result;
    }

    public boolean isTrue(Object... args){
        // 没传参数
        if (args == null || args.length == 0) {
            return false;
        }

        // 第一个参数为空
        if (args[0] == null) {
            return false;
        }
        return checkBoolean(args);
    }

    public boolean isFalse(Object... args){
        // 没传参数
        if (args == null || args.length == 0) {
            return false;
        }

        // 第一个参数为空
        if (args[0] == null) {
            return false;
        }
        return checkBoolean(args)?false:true;
    }

    public boolean checkBoolean(Object... args){
        for(int i=0; i<args.length; i++){
            Object obj = args[i];
            if (obj instanceof String) {
                return ((String) obj).equalsIgnoreCase("true") || ((String) obj).equalsIgnoreCase("1");
            }
            if(obj instanceof Boolean){
                return (boolean) obj;
            }
        }

        return false;
    }

    public boolean notNull(Object... args){
        if(args.length == 0){
            return false;
        }
        for(int i=0; i<args.length; i++){
            Object obj = args[i];
            if(obj instanceof String){
                if(!StringUtils.hasLength(obj.toString())){
                    return false;
                }
            }else{
                if(obj == null){
                    return false;
                }
            }
        }

        return true;
    }

    public boolean gt(Object a, Object b){
        int result = CommonUtil.compare(Double.valueOf(a.toString()), Double.valueOf(b.toString()));
        return result > 0;
    }

    public boolean gte(Object a, Object b){
        int result = CommonUtil.compare(Double.valueOf(a.toString()), Double.valueOf(b.toString()));
        return result >= 0;
    }

    public boolean lt(Object a, Object b){
        int result = CommonUtil.compare(Double.valueOf(a.toString()), Double.valueOf(b.toString()));
        return result < 0;
    }

    public boolean lte(Object a, Object b){
        int result = CommonUtil.compare(Double.valueOf(a.toString()), Double.valueOf(b.toString()));
        return result <= 0;
    }

    public boolean et(Object a, Object b){

        if(a instanceof String){
            return a.toString().equals(b.toString());
        }else if(a instanceof Double){
            int result = CommonUtil.compare(Double.valueOf(a.toString()), Double.valueOf(b.toString()));
            return result == 0;
        }
        return a.equals(b);
//        return false;
    }

    public boolean noteq(Object a, Object b){
        if(a instanceof String){
            return !a.toString().equals(b.toString());
        }else if(a instanceof Double){
            int result = CommonUtil.compare(Double.valueOf(a.toString()), Double.valueOf(b.toString()));
            return !(result == 0);
        }
        return !a.equals(b);
    }

    public int length(Object a){
        if(a instanceof String){
            return ((String) a).length();
        }
        return a.toString().length();
    }

    public int words(Object value) {
        String text = (String) value;
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int chineseCharacterCount = 0;
        int englishWordCount = 0;

        // 统计中文字符的数量
        for (char c : text.toCharArray()) {
            if (c >= '\u4e00' && c <= '\u9fa5') {
                chineseCharacterCount++;
            }
        }

        // 统计英文单词的数量
        String[] words = text.split("[^a-zA-Z]+");
        for (String word : words) {
            if (!word.isEmpty()) {
                englishWordCount++;
            }
        }

        // 返回中文字符数和英文单词数的总和
        return chineseCharacterCount + englishWordCount;
    }

    public boolean allEquals(List<?> list, String fieldName, Object expectedValue) {
        if (list == null || list.isEmpty()) return false;

        try {
            return list.stream().allMatch(item -> {
                try {
                    Field field = item.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object fieldValue = field.get(item);
                    if(fieldValue instanceof String) {
                        return fieldValue.toString().equals(expectedValue.toString());
                    }else{
                        return fieldValue.equals(expectedValue);
                    }

                } catch (Exception e) {
                    return false;
                }
            });
        } catch (Exception e) {
            return false;
        }
    }

    public boolean allNotEquals(List<?> list, String fieldName, Object expectedValue) {
        if (list == null || list.isEmpty()) return false;

        try {
            return list.stream().allMatch(item -> {
                try {
                    Field field = item.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object fieldValue = field.get(item);
                    if(fieldValue instanceof String) {
                        return fieldValue.toString().equals(expectedValue.toString());
                    }else{
                        return fieldValue.equals(expectedValue);
                    }

                } catch (Exception e) {
                    return false;
                }
            });
        } catch (Exception e) {
            return false;
        }
    }

    public static void main(String[] args) {
        CalculateCondition condition = new CalculateCondition();
        ExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("calculate", condition);

//        String originExpression = "#calculate.gt(#calculate.length(#asrText), 45)";
//        String asrText = "right:thenumberyouarecallingfromwho'12345.1232";
//
//        context.setVariable("asrText", asrText);

        // 执行 SpEL 表达式
//        boolean result = (boolean) parser.parseExpression(originExpression).getValue(context);

        List<KeywordAnalysisResponse.KeywordResult> list = new ArrayList<>();

        KeywordAnalysisResponse.KeywordResult keywordResult = new KeywordAnalysisResponse.KeywordResult();
        keywordResult.setResult("1");
        list.add(keywordResult);
        KeywordAnalysisResponse.KeywordResult keywordResult2 = new KeywordAnalysisResponse.KeywordResult();
        keywordResult2.setResult("1");
        list.add(keywordResult2);
        context.setVariable("list", list);
        boolean result = parser.parseExpression(
                "#calculate.allEquals(#list, 'result', 1)"
        ).getValue(context, Boolean.class);


        log.info("result:{}", result);

        testLength();
        testList();
        testIsStream();
        testIsFalse();
        testContains();
    }

    private static void testLength(){
        ExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext context = new StandardEvaluationContext();

        String asr = "这是一个asr测试文本，今日油价涨幅0.85元，目前95号汽油达到了9元多，10元时代是要来了吗";

        context.setVariable("asr", asr);
        boolean result = parser.parseExpression("#asr.length > 100").getValue(context, Boolean.class);
        log.info("result:{}", result);
    }

    private static void testList(){
        ExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext context = new StandardEvaluationContext();

        List<List<Object>> list = new ArrayList<>();

        list.add(Arrays.asList("张先生，xxxxx", Arrays.asList(1.6, 7.6), 0, "neutral"));
        list.add(Arrays.asList("xxxx你今天下午五点前不花钱xxx", Arrays.asList(11.0, 17.4), 0, "angry"));
        list.add(Arrays.asList("xxx天天打跟催运一样，我就去举报你。", Arrays.asList(17.6, 24.8), 1, "disgusted"));

        context.setVariable("list", list);

        boolean result = parser.parseExpression("#list.?[#this[3] == 'disgusted1'].size() > 0").getValue(context, Boolean.class);
        log.info("result:{}", result);
    }

    private static void testIsStream() {
        ExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext context = new StandardEvaluationContext();

        Task task = new Task();
        task.setStream(true);

        context.setVariable("task", task);

        // 判断 isStream == false
        Boolean result = parser.parseExpression( "!#task.stream" ).getValue(context, Boolean.class);

        log.info("result:{}", result);
    }

    private static void testIsFalse(){
        CalculateCondition condition = new CalculateCondition();
        ExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("calculate", condition);

        Object status = null;
        context.setVariable("status", status);
        boolean result = parser.parseExpression(
                "#calculate.isFalse(#status)"
        ).getValue(context, Boolean.class);
        log.info("isFalse result:{}", result);
    }

    private static void testContains(){
        CalculateCondition condition = new CalculateCondition();
        ExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext context = new StandardEvaluationContext();

        Object status = "test高风险123";
        context.setVariable("status", status);
        boolean result = parser.parseExpression( "#status.contains('低风险')" ).getValue(context, Boolean.class);
        log.info("testContains result:{}", result);
    }
}
