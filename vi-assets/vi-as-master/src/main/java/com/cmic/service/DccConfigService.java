package com.cmic.service;

import com.cmic.dao.NumDao;
import com.cmic.pojo.CheckNum;
import com.cmic.util.SipUtil;
import com.cmic.util.SpecialCallRegulationPrefixTries;
import com.dcc.config.dao.DccConfigDao;
import com.dcc.config.model.*;
import gov.nist.javax.sip.message.SIPRequest;
import lombok.Getter;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

/**
 * @author XH
 * @description: 缓存企业配置的相关信息
 * @date 2025/7/31 10:28
 */
public class DccConfigService {

    @Getter
    private static final DccConfigService instance = new DccConfigService();
    private HashMap<Integer, ConfigProvince> configProvince = new HashMap<>();
    private HashMap<Integer, ConfigCity> configCity = new HashMap<>();
    private HashMap<Integer, ConfigPlatform> configPlatform = new HashMap<>();
    private HashMap<Integer, ConfigUser> configUser = new HashMap<>();
    private HashMap<String, ConfigApplication> configApplication = new HashMap<>();
    /**
     * 地市,特服号码配置信息
     */
    private Map<Integer, CityBusinessConfig> cityBusinessConfig = new HashMap<>();
    private Set<String> checkNumSet = new HashSet<>();
    private List<String> gzhRouteRegexList = new ArrayList<>();
    private HashMap<Integer, ArrayList<CheckNum>> checkNumHashMap = new HashMap<>();

    // 区号和ConfigCity的HashMap
    private HashMap<Integer, ConfigCity> codeMap = new HashMap<>();
    // 省份中文名称和Config

    // 精准匹配的配置数据用HashMap存储，前缀匹配的配置数据用前缀树存储
    private HashMap<String, HashMap<Integer, SpecialCallRegulationConfig>> exactConfigHashMap = new HashMap<>();
    private SpecialCallRegulationPrefixTries prefixTries = new SpecialCallRegulationPrefixTries();

    public void init(){
        reload();
    }

    public void reload(){
        HashMap<Integer, ConfigProvince> configProvinceTemp = new HashMap<>();
        HashMap<Integer, ConfigCity> configCityTemp = new HashMap<>();
        HashMap<Integer, ConfigCity> codeMapTemp = new HashMap<>();
        HashMap<Integer, ConfigPlatform> configPlatformTemp = new HashMap<>();
        HashMap<Integer, ConfigUser> configUserTemp = new HashMap<>();
        HashMap<String, ConfigApplication> configApplicationTemp = new HashMap<>();
        Map<Integer, CityBusinessConfig> cityBusinessConfigTmp = new HashMap<>();
        Set<String> checkNumTmp = new HashSet<>();
        List<String> gzhRouteRegexTmp = new ArrayList<>();

        ArrayList<ConfigProvince> configProvinces = DccConfigDao.getInstance().selectConfigProvince();
        for(ConfigProvince province : configProvinces){
            configProvinceTemp.put(province.getId(), province);
        }
        ArrayList<ConfigCity> configCities = DccConfigDao.getInstance().selectAllCityConfig();
        for(ConfigCity city : configCities){
            configCityTemp.put(city.getId(), city);
        }
        for(ConfigCity city : configCities){
            codeMapTemp.put(city.getCode(), city);
        }
        ArrayList<ConfigPlatform> configPlatforms = DccConfigDao.getInstance().selectAllConfigPlatform();
        for (ConfigPlatform platform : configPlatforms){
            configPlatformTemp.put(platform.getId(), platform);
        }
        ArrayList<ConfigUser> configUsers = DccConfigDao.getInstance().selectAllConfigUser();
        for(ConfigUser user : configUsers){
            configUserTemp.put(user.getId(), user);
        }
        ArrayList<ConfigApplication> configApplications = DccConfigDao.getInstance().selectAllConfigApplication();
        for(ConfigApplication application : configApplications){
            configApplicationTemp.put(application.getAppId(), application);
        }
        for (CityBusinessConfig cityBusinessConfig :
                DccConfigDao.getInstance().selectAllCityBusinessConfig()) {
            cityBusinessConfigTmp.put(cityBusinessConfig.getCityId(), cityBusinessConfig);
        }
        for (ConfigCallTestNum testNum : DccConfigDao.getInstance().selectAllConfigCallTestNum()) {
            checkNumTmp.add(testNum.getNum());
        }
        for (String s : DccConfigDao.getInstance().selectAllGzhAddressRegex()) {
            gzhRouteRegexTmp.add(s);
        }

        //特服号码规整表
        HashMap<String, HashMap<Integer, SpecialCallRegulationConfig>> newExactMap = new HashMap<>();
        SpecialCallRegulationPrefixTries newPrefixTries = new SpecialCallRegulationPrefixTries();
        ArrayList<SpecialCallRegulationConfig> specialCallRegulationConfigArrayList =  DccConfigDao.getInstance().selectSpecialCalleeRegulationConfig();
        for (SpecialCallRegulationConfig regulationConfig : specialCallRegulationConfigArrayList){
            String number = regulationConfig.getNumber();
            Integer provinceId = regulationConfig.getProvince();
            int isRegex = regulationConfig.getMatchType();
            if (isRegex == 0){
                if (!newExactMap.containsKey(number)){
                    HashMap<Integer, SpecialCallRegulationConfig> tmpMap = new HashMap<>();
                    tmpMap.put(provinceId,regulationConfig);
                    newExactMap.put(number,tmpMap);
                }else{
                    newExactMap.get(number).put(provinceId,regulationConfig);
                }
            }else {
               newPrefixTries.insert(number, provinceId, regulationConfig);
            }
        }
        this.exactConfigHashMap = newExactMap;
        this.prefixTries = newPrefixTries;

        HashMap<Integer, ArrayList<CheckNum>> tmpCheckNumHashMap = new HashMap<>();
        ArrayList<CheckNum> checkList = NumDao.getInstance().selectAllCheckNums();
        for (CheckNum num : checkList) {
            if (!tmpCheckNumHashMap.containsKey(num.getUser_id())) {
                ArrayList<CheckNum> check_List = new ArrayList<>();
                check_List.add(num);
                tmpCheckNumHashMap.put(num.getUser_id(), check_List);
            } else {
                tmpCheckNumHashMap.get(num.getUser_id()).add(num);
            }
        }

        this.configProvince = configProvinceTemp;
        this.configCity = configCityTemp;
        this.configPlatform = configPlatformTemp;
        this.configUser = configUserTemp;
        this.configApplication = configApplicationTemp;
        this.cityBusinessConfig = cityBusinessConfigTmp;
        this.checkNumSet = checkNumTmp;
        this.gzhRouteRegexList = gzhRouteRegexTmp;
        this.checkNumHashMap = tmpCheckNumHashMap;
        this.codeMap = codeMapTemp;
    }

    public ConfigProvince getConfigProvince(int provinceId) {
        return configProvince.get(provinceId);
    }

    public ConfigCity getConfigCity(int cityId) {
        return configCity.get(cityId);
    }

    public HashMap<Integer, ConfigCity> getConfigCityMap(){
        return configCity;
    }

    public ConfigPlatform getConfigPlatform(int platformId) {
        return configPlatform.get(platformId);
    }

    public ConfigUser getConfigUser(int userId) {
        return configUser.get(userId);
    }

    public ConfigApplication getConfigApplication(String applicationId) {
        return configApplication.get(applicationId);
    }

    public BaseProdNum getProdNum(long prodNum) {
        return NumDao.getInstance().selectBaseProdNumByNumber(prodNum);
    }

    /**
     * 根据号码归属的地市id查询关于特服号码和地市相关的配置信息
     *
     * @param cityId 号码归属的地市id
     * @return 返回指定的地市，特服号码等配置信息
     */
    public CityBusinessConfig getCityBusinessConfig(int cityId) {
        return cityBusinessConfig.get(cityId);
    }

    //客户自身拨测
    public boolean checkIsCallTestNum(String num) {
//        这里再去掉所有的主叫开头0
        while (num.startsWith("0") && num.length() > 1) {
            num = num.substring(1);
        }
        return checkNumSet.contains(num);
    }

    /**
     * 根据请求消息判断是否属于工作号业务
     *
     * @param request 请求消息
     * @return 是否属于工作号业务
     */
    public boolean checkIsGzhByRegex(SIPRequest request) {
        // 判断请求中Route头域是否包含符合工作号域名规则的域名，有则返回域名，无则返回空字符串
        String routeAddress = SipUtil.getSpecialRouteValueByRegex(request, gzhRouteRegexList);
        // 域名中需要包含orig
        if (routeAddress.contains("orig")) {
            return true;
        }
        return false;
    }

    /**
     * 根据请求消息判断是否属于工作号业务
     *
     * @param request 请求消息
     * @return 是否属于工作号业务
     */
    public boolean checkIsGzhByRegexWithoutOrig(SIPRequest request) {
        // 判断请求中Route头域是否包含符合工作号域名规则的域名，有则返回域名，无则返回空字符串
        String routeAddress = SipUtil.getSpecialRouteValueByRegex(request, gzhRouteRegexList);
        // 包含工作号域名回true
        if (!StringUtils.isBlank(routeAddress)) {
            return true;
        }
        return false;
    }

    public int getPlatformExtID(BaseProdNum prodNum) {
        ConfigPlatform configPlatform = DccConfigService.getInstance().getConfigPlatform(prodNum.getPlatformId());
        return configPlatform.getExtId();
    }

    public Pair<Integer, String> isCheckNum(int userId, String callerNum) {
        ArrayList<CheckNum> tempList = checkNumHashMap.get(userId);
        if (tempList != null) {
            for (CheckNum checkNum : tempList) {
                if (callerNum.startsWith(checkNum.getNum())) {
                    return Pair.of(checkNum.getCheck_user_id(), checkNum.getCheck_application_id());
                }
            }
        }
        return null;
    }

   /* public HashMap<String, HashMap<Integer, SpecialCallRegulationConfig>> getSpecialCallRegulationConfigHashMap() {
        return specialCallRegulationConfigHashMap;
    }*/

    public HashMap<Integer, SpecialCallRegulationConfig> getSpecialCallRegulationByNumber(String number){
        HashMap<Integer, SpecialCallRegulationConfig> tmpExactMap = exactConfigHashMap.get(number);
        if (tmpExactMap != null){
            return tmpExactMap;
        }
        return prefixTries.find(number);
    }



    //通过区号获取configCity
    public ConfigCity getConfigCityByAreaCode(int cityCode){
        return codeMap.get(cityCode);
    }
}


//todo:依赖文件中少了特服号码规整表对应的类
/*
public class SpecialCallRegulationConfig {
    private int province;
    private String number;
    private int type;
    public static final ResultSetConvertor<SpecialCallRegulationConfig> BUILDER = new ResultSetConvertor<SpecialCallRegulationConfig>() {
        public SpecialCallRegulationConfig convert(ResultSet rs) throws SQLException {
            return new SpecialCallRegulationConfig(rs.getInt("province"), rs.getString("number"), rs.getInt("type"));
        }
    };

    public SpecialCallRegulationConfig(int province, String number, int type) {
        this.province = province;
        this.number = number;
        this.type = type;
    }

    public int getProvince() {
        return this.province;
    }

    public void setProvince(int province) {
        this.province = province;
    }

    public String getNumber() {
        return this.number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public int getType() {
        return this.type;
    }

    public void setType(int type) {
        this.type = type;
    }
}
*/
