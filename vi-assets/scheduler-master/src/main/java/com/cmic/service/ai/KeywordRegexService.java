package com.cmic.service.ai;


import com.cmic.engine.XmlEventPublisher;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.engine.event.MqEvent;
import com.cmic.pojo.bo.AIModelEnum;
import com.cmic.pojo.bo.Attribute;
import com.cmic.pojo.dto.ErrorCode;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import com.cmic.service.MqService;
import com.cmic.service.task.SubTaskService;
import com.cmic.service.task.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class KeywordRegexService {
    @Autowired
    private MqService mqService;
    @Autowired
    private XmlEventPublisher xmlEventPublisher;
    @Resource
    private TaskService taskService;
    @Autowired
    private SubTaskService subTaskService;

    public void match(MqEntityEvent event) {
        Task task = event.getTask();
        SubTask subTask = event.getSubTask();

        subTaskService.beforeSendModel(task, subTask, AIModelEnum.WORD_REGEX.getName());
        String code = ErrorCode.SUCCESS.getCode();
        String message = ErrorCode.SUCCESS.getMessage();
        Map<String, Object> responseMap = new HashMap<>();
        try {
            String asrText = (String) subTask.getResponseResults().get(Attribute.ASR_TEXT.getName());

            Map<String, Object> attributes = task.getAttributes();
            List<Map<String, String>> wordRegex = (List<Map<String, String>>) attributes.get("wordRegex");
            List<Map<String, Object>> result = getMatchResult(wordRegex, asrText);
            log.info("TaskId:{}, jobId:{}, processId:{}, priority:{}, keyword regex success", task.getId(), subTask.getId(), task.getProcessId(), task.getPriority());

//            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put(Attribute.WORD_REGEX_STATUS.getName(), true);
            responseMap.put(Attribute.WORD_REGEX_RESULT.getName(), result);
//            subTaskService.updateSubTaskResponseMap(subTask, responseMap);
        } catch (Exception e) {
            log.error("TaskId:{}, jobId:{}, processId:{}, keyword regex failed:", task.getId(), subTask.getId(), task.getProcessId(), e);
            code = ErrorCode.ERROR.getCode();
            message = ErrorCode.ERROR.getMessage();
            responseMap.put(Attribute.WORD_REGEX_STATUS.getName(), false);
//            subTaskService.updateSubTaskErrorMessage(subTask, AIModelEnum.WORD_REGEX.getName(), "matched unknown error");
//            subTaskService.updateSubTaskResponseResult(subTask, Attribute.WORD_REGEX_STATUS.getName(), false);
        }
        subTask = subTaskService.afterResponseModel(code, message, task, subTask, AIModelEnum.WORD_REGEX.getName(), responseMap);
        xmlEventPublisher.mqEventPublish(MqEvent.WORD_REGEX_RESPONSE, task, subTask);
    }

    private static List<Map<String, Object>> getMatchResult(List<Map<String, String>> regexMapList, String text) {
        List<Map<String, Object>> resultList = new ArrayList<>();
        if (regexMapList == null || regexMapList.isEmpty()) {
            return resultList;
        }
        for (Map<String, String> regexMap : regexMapList) {
            String reId = regexMap.get("reId");
            String regex = regexMap.get("regex");

            Map<String, Object> resultMap = new HashMap<>();
            Map<String, Object> word = getMatchResult(regex, text);
            resultMap.put("reId", reId);
            resultMap.put("word", word);
            resultList.add(resultMap);
        }
        return resultList;
    }

    private static Map<String, Object> getMatchResult(String wordRegex, String text) {
        Pattern pattern = Pattern.compile(wordRegex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);

        List<String> match = new ArrayList<>();
        List<List> group = new ArrayList<>();
        while (matcher.find()) {
            match.add(matcher.group(0));
            List<String> groupItem = new ArrayList<>();
            for (int i = 1; i <= matcher.groupCount(); i++) {
                if (matcher.group(i) != null) {
                    groupItem.add(matcher.group(i));
                }
            }
            group.add(groupItem);
        }
        Map<String, Object> wordResult = new HashMap<>();
        wordResult.put("match", match);
        wordResult.put("group", group);

        return wordResult;
    }

    public static void main(String[] args) {
        // 测试正则：匹配所有字母组成的单词
//        String regex = "(?=[\\s\\S]*((?:收到|到了|接到)[\\s\\S]{0,10}?(?:短信|信息|快递)|(?:收到|到了|接到)[\\s\\S]{0,10}?(?:包裹|快件)|(?:邮|寄)[\\s\\S]{0,10}?(?:快递|包裹|快件)|(?:有|接|收)[\\s\\S]{0,10}?(?:快递|包裹)|(?:查|发)[\\s\\S]{0,10}?(?:快件|快递|包裹)|(?:快件|短信|快递)[\\s\\S]{0,10}?(?:收了|到了|来了)|(?:收了|到了|来了)[\\s\\S]{0,10}?(?:快件|短信|快递)))(?=[\\s\\S]*(抖音|快手|拼多多|微信|微众银行|微粒贷|腾讯微保|京东|小银卡|小赢卡贷))(?=[\\s\\S]*((?:直播|电商)[\\s\\S]{0,10}?会员|会员[\\s\\S]{0,10}?(?:直播|电商)|(?:电子)[\\s\\S]{0,10}?(?:合同|保单|保险)|(?:合同|保单|保险)[\\s\\S]{0,10}?电子|(?:合同|交易)[\\s\\S]{0,10}?保单|保单[\\s\\S]{0,10}?(?:合同|交易)|会员卡|会员权益|百万保障|百万保险|(?:拼多多)[\\s\\S]{0,10}?中国人寿|中国人寿[\\s\\S]{0,10}?拼多多|百万账户))(?=[\\s\\S]*((?:即将|开始|产生)[\\s\\S]{0,10}?(?:扣费|扣款|扣取费用)|(?:扣费|扣款|扣取费用)[\\s\\S]{0,10}?(?:即将|开始|产生)|(?:自动|到期)[\\s\\S]{0,10}?(?:扣费|扣款|扣取费用)|(?:扣费|扣款|扣取费用)[\\s\\S]{0,10}?(?:自动|到期)|(?:免费使用|免费领取|免费试用)[\\s\\S]{0,10}?到期|到期[\\s\\S]{0,10}?(?:免费使用|免费领取|免费试用)|(?:卡贷|账单|使用)[\\s\\S]{0,10}?到期|到期[\\s\\S]{0,10}?(?:卡贷|账单|使用)|(?:会员|权限|保单)[\\s\\S]{0,10}?(?:关闭|取消|到期)|(?:关闭|取消|到期)[\\s\\S]{0,10}?(?:会员|权限|保单)|(?:保险|保障|合同)[\\s\\S]{0,10}?(?:关闭|取消|到期)|(?:关闭|取消|到期)[\\s\\S]{0,10}?(?:保险|保障|合同)|(?:关闭|取消)[\\s\\S]{0,10}?(?:电商会员|直播会员|抖音会员)|(?:电商会员|直播会员|抖音会员)[\\s\\S]{0,10}?(?:关闭|取消)|业务[\\s\\S]{0,10}?(?:关闭|取消)|(?:关闭|取消)[\\s\\S]{0,10}?业务|(?:保险|保障|保单)[\\s\\S]{0,10}?(?:退掉|关掉|开通)|(?:退掉|关掉|开通)[\\s\\S]{0,10}?(?:保险|保障|保单)|到期[\\s\\S]{0,10}?扣钱))[\\s\\S]*";
//        String text = "right:是的，这边有一个抖音官方平台，委托我们给您送的一个直播会员服者协议会员。你的会员免费使用到期到了，使用，到了即将产生\n" +
//                      "left:你好，我收到一个短信说到了。";

        String regex = "(?:((?:营业执照|税务|申报|代办))|(?:[\\s\\S]*?((?:年报|工商|罚款|年审|滞纳金))[\\s\\S]*?((?:年报|工商|罚款|年审|滞纳金)))|(?:[\\s\\S]*?((?:年报|工商|罚款|年审|滞纳金|注销|提交|补录|报税|法人|个体户|登录|公章|服务费|公示|年检|统一社会信用代码|备案))[\\s\\S]*?((?:年报|工商|罚款|年审|滞纳金|注销|提交|补录|报税|法人|个体户|登录|公章|服务费|公示|年检|统一社会信用代码|备案))[\\s\\S]*?((?:年报|工商|罚款|年审|滞纳金|注销|提交|补录|报税|法人|个体户|登录|公章|服务费|公示|年检|统一社会信用代码|备案))))|(?:((?:芝麻分|花呗))|(?:[\\s\\S]*?((?:额度|审核))[\\s\\S]*?((?:额度|审核)))|(?:[\\s\\S]*?((?:额度|审核|信用|放款|下款|分期|结清|首付|免押|租赁|借呗|月供|冻结))[\\s\\S]*?((?:额度|审核|信用|放款|下款|分期|结清|首付|免押|租赁|借呗|月供|冻结))[\\s\\S]*?((?:额度|审核|信用|放款|下款|分期|结清|首付|免押|租赁|借呗|月供|冻结))))|(?:((?:退息|退费))|(?:[\\s\\S]*?((?:网贷|结清|还款|减免|协商|合同|违规|投诉|年利率|流水|IRR|12378|黑猫|监管|代理|委托|芝麻信用))[\\s\\S]*?((?:网贷|结清|还款|减免|协商|合同|违规|投诉|年利率|流水|IRR|12378|黑猫|监管|代理|委托|芝麻信用))[\\s\\S]*?((?:网贷|结清|还款|减免|协商|合同|违规|投诉|年利率|流水|IRR|12378|黑猫|监管|代理|委托|芝麻信用))))|(?:((?:证券|大盘|牛股|投顾|股市|股票|早盘|盘中|建仓|涨停|仓位))|(?:[\\s\\S]*?((?:个股|内部交流群|止损|内部|交流|加仓|直播间|盈利|涨起来|仓位|走势|个点))[\\s\\S]*?((?:个股|内部交流群|止损|内部|交流|加仓|直播间|盈利|涨起来|仓位|走势|个点)))|(?:[\\s\\S]*?((?:个股|内部交流群|止损|内部|交流|加仓|直播间|止盈|盈利|减仓|投资|理财|入金|开户|社群|私募|讨论|高开低走|支票|短线|个点))[\\s\\S]*?((?:个股|内部交流群|止损|内部|交流|加仓|直播间|止盈|盈利|减仓|投资|理财|入金|开户|社群|私募|讨论|高开低走|支票|短线|个点))[\\s\\S]*?((?:个股|内部交流群|止损|内部|交流|加仓|直播间|止盈|盈利|减仓|投资|理财|入金|开户|社群|私募|讨论|高开低走|支票|短线|个点))))";
        String text = "喂，你好，*是吗？刚刚申请的贷款是你本人操作的吗？就是下午就是晚上七点的时候申请的，嗯，这边是审批的额度已经下来了，总的额度是一万元。你这边现在能立马提现到账的额度是两千，然后剩下的额度是七天后自动激活。你这边需要吗？嗯，你这边不是平台，我们这边就是人工放款通道，你有需要的话，就是这个钱直接打到你的银行卡上了。嗯，什么原因呢？钱额度太低了。这七天过后，你把这个两千的本金给他还清了以后可以解锁，剩剩下的八千的额度啊啊，你有需要的话，打这个电话啊，嗯好，再见。";
        Map<String, Object> result = getMatchResult(regex, text);
        System.out.println(result);
    }
}
