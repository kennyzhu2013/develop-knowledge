package com.cmic;

import com.cmic.config.xml.Dom4jUtil;
import com.cmic.config.xml.EventXml;
import com.cmic.engine.condition.CalculateCondition;
import io.prometheus.client.CollectorRegistry;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@SpringBootTest
public class ELxmlBeanTest {
    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private com.cmic.SomeService someService;
    @Autowired
    private CalculateCondition condition;
    @MockBean
    private CollectorRegistry collectorRegistry;

    @Test
    public void test1(){
        String fileName = "sources/test/asr_keyword_scenario.xml";
        File file = new File(fileName);

        Map<String, EventXml> exportEventMap = new HashMap<>();
        List<EventXml> eventXmlList = Dom4jUtil.doEventXml(file, exportEventMap);
        log.info(eventXmlList.toString());
    }

    @Test
    public void test2(){
        ExpressionParser parser = new SpelExpressionParser();

        // 创建 EvaluationContext（评估上下文）
        StandardEvaluationContext context = new StandardEvaluationContext();

        // 添加你想要评估的变量，例如，someService
        SomeService someService = new SomeService(); // 请替换成实际的 someService 对象
        context.setVariable("someService", someService);

        // 执行 SpEL 表达式
        Object result = parser.parseExpression("#someService.processMap(T(java.util.Arrays).asList(10, 20))")
                .getValue(context);

        // 输出结果
        System.out.println("Result of executing SpEL expression: " + result);
    }

    @Test
    public void test3(){
        ExpressionParser parser = new SpelExpressionParser();

        // 创建 EvaluationContext（评估上下文）
        StandardEvaluationContext context = new StandardEvaluationContext();

        // 添加你想要评估的变量，例如，someService
        SomeService someService = new SomeService(); // 请替换成实际的 someService 对象
        context.setVariable("condition", condition);

        // 执行 SpEL 表达式
        Object result = parser.parseExpression("#condition.isTrue(T(java.util.Arrays).asList(false, 1))")
                .getValue(context);

        Object result2 = parser.parseExpression("#condition.moreThan(5, 10)")
                .getValue(context);

        // 输出结果
        System.out.println("Result of executing SpEL expression: " + result);
        System.out.println("Result of executing SpEL expression: " + result2);
    }

    static class SomeService {
        public String processMap(List list) {
            // 你的处理逻辑在这里
            return "Processed map: " + list.toString();
        }
    }

    private Object calculate(Map<String , Object> map) {
        ExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("someService", someService);


        // 使用Spring EL表达式执行计算操作
        String expression = String.valueOf(map.get("operation"));
        return parser.parseExpression(expression).getValue(context);
    }


}
