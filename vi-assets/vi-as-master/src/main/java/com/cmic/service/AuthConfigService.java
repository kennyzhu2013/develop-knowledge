package com.cmic.service;

import com.cmic.config.AppConfig;
import com.cmic.dao.*;
import com.cmic.util.DevHelp;
import com.cmic.util.PhoneNumUtil;
import lombok.Getter;

import java.sql.Timestamp;
import java.util.*;

/**
 * 企业配置信息
 *
 * @author XH
 * @version 1.0
 * @date 2026-03-15
 */
public class AuthConfigService {

    @Getter
    private static final AuthConfigService instance = new AuthConfigService();

    private AuthConfigService(){

    }
    /**
     * 缓存主叫号码报备信息
     */
    private HashMap<String, BizNumAuthDo> accurateCallerNumAuthMap = new  HashMap<>();
    private TreeMap<String, BizNumAuthDo> prefixCallerNumAuthMap = new TreeMap<>((k1, k2) -> {
        // 降序排列：长度长的排在前面
        return Integer.compare(k2.length(), k1.length());
    });
    private HashMap<Integer, ChannelDo> channelMap = new HashMap<>();
    private HashMap<Integer, CompanyDo> companyMap = new HashMap<>();
    private HashMap<Integer, CustomerDo> customerMap = new HashMap<>();
    @Getter
    private final ArrayList<String> gzhDomainRegexList =  new ArrayList<>(Collections.singleton(".*\\.gzh\\.*[a-z]*\\.chinamobile\\.com.*"));

    public void init(){
        reloadNumConf();
        reloadBizInfo();
    }

    /**
     * 全量刷新所有企业和号码配置信息
     */
    public void reloadNumConf(){

        HashMap<String, BizNumAuthDo> accurateCallerNumAuthMapTemp = new  HashMap<>();
        TreeMap<String, BizNumAuthDo> prefixCallerNumAuthMapTemp = new  TreeMap<>();
        ArrayList<BizNumAuthDo> accurateCallerNumAuthList = UserQcDao.getInstance().selectAllCallerNumAuth();
        for(BizNumAuthDo callerNumAuthDo :  accurateCallerNumAuthList){
            if(callerNumAuthDo.getMatchType() == 1){
                accurateCallerNumAuthMapTemp.put(PhoneNumUtil.getPure11NumberWithout0or86(callerNumAuthDo.getNum()), callerNumAuthDo);
            }else{
                prefixCallerNumAuthMapTemp.put(callerNumAuthDo.getNum(), callerNumAuthDo);
            }
        }
        accurateCallerNumAuthMap = accurateCallerNumAuthMapTemp;
        prefixCallerNumAuthMap = prefixCallerNumAuthMapTemp;
    }

    /**
     * 全量刷新所有企业和号码配置信息
     */
    public void reloadBizInfo(){

        HashMap<Integer, ChannelDo> channelMapTemp = new HashMap<>();
        HashMap<Integer, CompanyDo> companyMapTemp = new HashMap<>();
        HashMap<Integer, CustomerDo> customerMapTemp = new HashMap<>();
        ArrayList<ChannelDo> channelDoArrayList = UserQcDao.getInstance().selectAllChannel();
        for(ChannelDo channelDo : channelDoArrayList){
            channelMapTemp.put(channelDo.getId(), channelDo);
        }
        channelMap = channelMapTemp;

        ArrayList<CompanyDo> companyDoArrayList = UserQcDao.getInstance().selectAllCompany();
        for(CompanyDo companyDo : companyDoArrayList){
            companyMapTemp.put(companyDo.getId(), companyDo);
        }
        companyMap = companyMapTemp;

        ArrayList<CustomerDo> customerDoArrayList = UserQcDao.getInstance().selectAllCustomer();
        for(CustomerDo customerDo : customerDoArrayList){
            customerMapTemp.put(customerDo.getId(), customerDo);
        }
        customerMap = customerMapTemp;
    }



    /**
     * 查询主叫号码对应的报备信息
     * @param callerNum 主叫号码
     * @return 主叫号码匹配的完整报备信息
     */
    public BizNumAuthDo getCallerNumAuthDo(String callerNum) {
        if (DevHelp.currentMode == 1){
            Timestamp now = new Timestamp(System.currentTimeMillis());
            return new BizNumAuthDo(0l, callerNum, 0, -1, -1, now, now);
        }

        // 去掉号码前缀，此处暂时不区分号码类型
        callerNum = PhoneNumUtil.getPure11NumberWithout0or86(callerNum);
        if(accurateCallerNumAuthMap.containsKey(callerNum)){
            return accurateCallerNumAuthMap.get(callerNum);
        } else {
            for(String prefix : prefixCallerNumAuthMap.keySet()){
                if(callerNum.startsWith(prefix)){
                    return prefixCallerNumAuthMap.get(prefix);
                }
            }
            return null;
        }
    }

    /**
     * 查询指定id的公司信息
     * @param channelId 公司id
     * @return 对应channelId的配置信息
     */
    public ChannelDo getChannel(int channelId) {
        return channelMap.get(channelId);
    }

    /**
     * 查询指定id的公司信息
     * @param companyId 公司id
     * @return 对应companyId的配置信息
     */
    public CompanyDo getCompany(int companyId) {
        return companyMap.get(companyId);
    }

    /**
     * 查询指定id的终端信息
     * @param customerId 终端id
     * @return 对应customerId的配置信息
     */
    public CustomerDo getCustomer(int customerId) {
        return customerMap.get(customerId);
    }
}
