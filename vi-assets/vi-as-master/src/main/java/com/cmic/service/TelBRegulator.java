package com.cmic.service;

import com.cmic.dao.NumDao;
import com.cmic.pojo.SpeTelbRule;
import com.cmic.util.SipUtil;
import com.dcc.config.cache.CityCache;
import com.dcc.config.dao.DccConfigDao;
import com.dcc.config.model.BaseProdNum;
import com.dcc.config.model.CityBusinessConfig;
import com.dcc.config.model.SpecialCalleeFormatRule;
import com.dcc.config.model.SpecialTelB;
import gov.nist.javax.sip.header.SIPHeader;
import gov.nist.javax.sip.message.SIPRequest;
import lombok.Getter;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

//后续是否可以用Redis的bitMap去做
public class TelBRegulator {
    //需要裸号送出的号码池
    public HashMap<String, Integer> num_pool = new HashMap<>();
    //public HashMap<String, Integer> fowardCaller_num_pool = new HashMap<>();
    public ArrayList<SpeTelbRule> rules = new ArrayList<>();
    private Logger logger = LoggerFactory.getLogger(getClass());

    public ArrayList<SpecialCalleeFormatRule> calleeFormatRules = new ArrayList<>();
    public HashSet<String> specialCalleeSet = new HashSet<>();

    private TelBRegulator() {
    }

    @Getter
    private static TelBRegulator instance = new TelBRegulator();

    /**
     * 初始化特殊被叫号码池（送出时需裸号送出）
     */
    public void initPool() {
        ArrayList<SpecialTelB> telBArrayList = DccConfigDao.getInstance().selectSpecialTelb();
        telBArrayList.forEach(specialTelB -> {
            num_pool.put(specialTelB.getNumber(), specialTelB.getId());
        });
        //初始化规则队列
        rules = NumDao.selectAllSpeRule();
        calleeFormatRules = DccConfigDao.getInstance().selectCalleeFormatRule();
        for (SpecialCalleeFormatRule calleeFormatRule : calleeFormatRules) {
            specialCalleeSet.add(calleeFormatRule.getNumber());
        }
    }

    /**
     * 根据ETCD监听事件进行reload操作
     */
    public void reload() {
        ArrayList<SpecialTelB> telBArrayList = DccConfigDao.getInstance().selectSpecialTelb();
        HashMap<String, Integer> newHashMap = new HashMap<>();
        telBArrayList.forEach(specialTelB -> {
            newHashMap.put(specialTelB.getNumber(), specialTelB.getId());
        });
        num_pool = newHashMap;
        rules = NumDao.selectAllSpeRule();

        ArrayList<SpecialCalleeFormatRule> calleeFormatRulesTemp = DccConfigDao.getInstance().selectCalleeFormatRule();
        HashSet<String> specialCalleeSetTemp = new HashSet<>();
        for (SpecialCalleeFormatRule calleeFormatRule : calleeFormatRules) {
            specialCalleeSetTemp.add(calleeFormatRule.getNumber());
        }
        calleeFormatRules = calleeFormatRulesTemp;
        specialCalleeSet = specialCalleeSetTemp;
    }

    /**
     * 接续前规整号码格式，并返回处理后的号码
     *
     * @param telB    原始被叫号码
     * @param request 初始invite
     * @return 处理过后的号码
     */
    // public String regulate(String telB, SIPRequest request) {
    //     String tempTel = telB;
    //     if (telB.startsWith("86")) {
    //         tempTel = tempTel.substring(2);
    //     }
    //     //特殊的特服号码裸号送出
    //     if (num_pool.containsKey(tempTel)) {
    //         //判断特服号码是否需要+86，返回处理过的号码
    //         return getSpecialServiceNumber(tempTel, request);
    //     }
    //
    //     //默认小于7位的都是特服号码，需裸号送出
    //     if (tempTel.length() < 7) {
    //         //判断特服号码是否需要+86，返回处理过的号码
    //         return getSpecialServiceNumber(tempTel, request);
    //     }
    //     //其他符合要求数据库配置的特服号码规则需裸号送出
    //     for (SpeTelbRule rule : rules) {
    //         if (tempTel.startsWith(rule.getSpeTelbHead()) && tempTel.length() == rule.getLength()) {
    //             if (rule.getDispplus86() == 0) {
    //                 //判断特服号码是否需要+86，返回处理过的号码
    //                 return getSpecialServiceNumber(tempTel, request);
    //             } else {
    //                 return String.format("tel:+86%s", tempTel);
    //             }
    //         }
    //     }
    //     // 因转接查询时不再加86，因此此处接续需要+86
    //     return String.format("tel:+86%s", telB);
    // }


    /**
     * 查询缓存中对于特服号码的配置信息，判断是否需要+86后送出，并返回处理后的号码
     *
     // * @param originNumber 需接续的号码
     * @return 返回处理后的号码
     */
    // private String getSpecialServiceNumber(String originNumber, SIPRequest request) {
    //     String homeAreaCodeFromFirstInvite = SipUtil.getHomeAreaCodeFromFirstInvite(request);
    //     if(StringUtils.isEmpty(homeAreaCodeFromFirstInvite)){
    //         return String.format("tel:%s", originNumber);
    //     }
    //     CityBusinessConfig cityBusinessConfig = DccConfigService.getInstance().getCityBusinessConfig(Integer.parseInt(homeAreaCodeFromFirstInvite));
    //     if (cityBusinessConfig == null) {
    //         // 默认不加前缀
    //         return String.format("tel:%s", originNumber);
    //     }
    //     // 号码是否需要+86送出
    //     boolean isNeedPrefix = cityBusinessConfig.getIsNeedPrefix() == 1;
    //     // 需要特别处理的个别号码集合
    //     String[] exclusionNumberList = cityBusinessConfig.getPrefixExclusion() != null ? cityBusinessConfig.getPrefixExclusion().split(",") : new String[]{};
    //     // 需要特别处理的个别号码集合
    //     HashSet<String> exclusionNumberSet = new HashSet<>();
    //     Collections.addAll(exclusionNumberSet, exclusionNumberList);
    //     // is_need_prefix 为1时号码需要加86送出，此时特别处理的号码集合此时不加86
    //     //  is_need_prefix 为0时号码裸号送出，此时特别处理的号码集合此时需要加86送出
    //     if (isNeedPrefix) {
    //         if (!exclusionNumberSet.contains(originNumber)) {
    //             return String.format("tel:+86%s", originNumber);
    //         } else {
    //             return String.format("tel:%s", originNumber);
    //         }
    //     } else {
    //         if (exclusionNumberSet.contains(originNumber)) {
    //             return String.format("tel:+86%s", originNumber);
    //         } else {
    //             return String.format("tel:%s", originNumber);
    //         }
    //     }
    // }


    private boolean isSpecialServicePhone(String phoneNumber) {
        int originCalleeLen = phoneNumber.length();
        boolean isSpecialServicePhone = (phoneNumber.startsWith("95") || phoneNumber.startsWith("96")) && originCalleeLen >= 5;
        isSpecialServicePhone = isSpecialServicePhone || (phoneNumber.startsWith("1") && originCalleeLen >= 3);
        return isSpecialServicePhone;
    }

    /**
     * 特服号码规整:95,96,1开头的特服号码
     *
     * @param request
     * @param telB
     * @return
     */
    // public String regulateSpecialServicePhone(SIPRequest request, String telB, BaseProdNum prodNum) throws Exception {
    //     String adjustedTelB = "";
    //     SIPHeader header = (SIPHeader) request.getHeader("P-Access-Network-Info");
    //     String headerValue = header.getHeaderValue();
    //     String[] attrArr = headerValue.split(";");
    //     String sbcDomainAreaCode = "";
    //     String idenCode = "";
    //     for (String attr : attrArr) {
    //         if (attr.startsWith("sbc-domain") && attr.contains("=")) {
    //             String[] attrVal = attr.split("=");
    //             String temp = attrVal[1];
    //             String[] valArr = temp.split("\\.");
    //             int len = valArr.length;
    //             sbcDomainAreaCode = len >= 2 ? valArr[1] : "";
    //             idenCode = len >= 3 ? valArr[2] : "";
    //             break;
    //         }
    //     }
    //
    //     if (StringUtils.isBlank(sbcDomainAreaCode)) {
    //         return TelBRegulator.getInstance().regulate(telB, request);
    //     }
    //
    //     if (sbcDomainAreaCode.startsWith("0")) {
    //         sbcDomainAreaCode = sbcDomainAreaCode.substring(1);
    //     }
    //     int areaCodeLen = sbcDomainAreaCode.length();
    //     int telBLen = telB.length();
    //     boolean isAreaCodeCarried = false;
    //     String calleeAreaCode = "";
    //     String originCallee = "";
    //
    //     if (telBLen > areaCodeLen) {
    //         calleeAreaCode = telB.substring(0, areaCodeLen);
    //         originCallee = telB.substring(areaCodeLen, telBLen);
    //         if (calleeAreaCode.equals(sbcDomainAreaCode) && isSpecialServicePhone(originCallee)) {
    //             //若用户拨打的区号与sbc-domain中区号一致，则将INVITE消息的被叫号码修改为“国家码+区号+12540+区域识别码+原被叫号码”
    //             adjustedTelB = "86" + sbcDomainAreaCode + "12540" + idenCode + originCallee;
    //             adjustedTelB = String.format("tel:+%s", adjustedTelB);
    //             return adjustedTelB;
    //         }
    //
    //
    //         //判断是否携带区号
    //         if (telBLen >= 5) {
    //             if (CityCache.setAreaCode.contains(calleeAreaCode)) {
    //                 isAreaCodeCarried = true;
    //             } else {
    //                 int otherAreaCodeLen = areaCodeLen == 2 ? 3 : 2;
    //                 calleeAreaCode = telB.substring(0, otherAreaCodeLen);
    //                 originCallee = telB.substring(otherAreaCodeLen, telBLen);
    //                 if (CityCache.setAreaCode.contains(calleeAreaCode)) {
    //                     isAreaCodeCarried = true;
    //                 }
    //             }
    //         }
    //
    //         boolean isSpecialServicePhone = false;
    //         if (isAreaCodeCarried) {
    //             isSpecialServicePhone = isSpecialServicePhone(originCallee);
    //         } else {
    //             isSpecialServicePhone = isSpecialServicePhone(telB);
    //         }
    //
    //         if (!isAreaCodeCarried && isSpecialServicePhone) {
    //             //若用户未拨区号，则将INVITE消息的被叫号码修改为“国家码+区号+12540+区域识别码+原被叫号码”；
    //             adjustedTelB = "86" + sbcDomainAreaCode + "12540" + idenCode + telB;
    //             adjustedTelB = String.format("tel:+%s", adjustedTelB);
    //             return adjustedTelB;
    //         }
    //
    //         if (isAreaCodeCarried && !isSpecialServicePhone && isSpecialServicePhone(telB)) {
    //             //这种情况有可能是用户未拨区号，则将INVITE消息的被叫号码修改为“国家码+区号+12540+区域识别码+原被叫号码”；
    //             adjustedTelB = "86" + sbcDomainAreaCode + "12540" + idenCode + telB;
    //             adjustedTelB = String.format("tel:+%s", adjustedTelB);
    //             return adjustedTelB;
    //         }
    //
    //         if (isAreaCodeCarried && !sbcDomainAreaCode.equals(calleeAreaCode) && isSpecialServicePhone) {
    //             //若用户拨打区号且拨打的区号与sbc-domain中区号不一致，则将INVITE消息的被叫号码规整为“国家码+区号+原被叫号码”；
    //             adjustedTelB = "86" + sbcDomainAreaCode + originCallee;
    //             adjustedTelB = String.format("tel:+%s", adjustedTelB);
    //             return adjustedTelB;
    //         }
    //
    //     }
    //
    //     return TelBRegulator.getInstance().regulate(telB, request);
    //
    // }

}
