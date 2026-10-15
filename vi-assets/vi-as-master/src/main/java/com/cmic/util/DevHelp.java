package com.cmic.util;

import com.cmic.sip.engine.DtmfEngine;
import com.cmic.cache.ConfigCache;
import com.cmic.config.ConstantConf;
import com.cmic.config.AppConfig;
import com.cmic.pojo.CallerCalleePrefixMap;
import com.cmic.service.MyRegistryService;
import com.discovery.RegistryService;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import static com.cmic.config.ConstantConf.TRANSFER_LIMIT_NUM_KEY;
import static com.cmic.config.ConstantConf.TRANSFER_LIMIT_PREFIX_KEY;

/**
 * @author XXXX
 * @date 2023/07/21
 */
public class DevHelp {

    private static final Logger logger = LoggerFactory.getLogger(DevHelp.class);

    /**
     * 0代表不用强制ASR，1代表需要
     */
    public static String forceAsr = "0";

    /**
     * 空字符串代表不用限制编码，其他则表示需要
     */
    public static String forceCodec = "";

    /**
     * 只有符合当前ID的定时任务可以进行复原操作,一般都是最后一条HTTP命令执行修改
     */
    public static String id = "";

    public static String[] supportedCodec = "amr-wb,amr,pcmu,pcma,telephone-event".split(",");

    private static boolean isCertSkippedByBindTime = false;

    private static List<CallerCalleePrefixMap> displayNoLimitCallerCalleePrefixMapList = null;
    private static List<String> calleeLimitNumPrefixList = null;

    public static int maxSession = 15000;

    // 是否允许呼叫前转（根据呼转号码频次规则）
    public static int transferCalleeAllow = 1;

    public static long transferCalleeTTL = 259200L;

    private static List<TransferLimitDto> transferWhitelistPrefixList = null;

    private static List<TransferLimitDto> transferBlacklistPrefixList = null;

    private static List<String> strategyHostList = null;

    public static boolean isRouteSipProxy = false;
    // 默认打开
    public static int auditStrategyAllow = 1;
    public static List<String> forceToGdICscfCity = new ArrayList<>();

    public static boolean publishMQThreadPool = true;

    public static HashSet<String> newCollectCodecStrategyCustomerSet = new HashSet<>();

    public static float dbOperRejectRatio = 0.7f;

    public static int initStartMode = 0;
    public static int currentMode = 0;

    /**
     * 初始化全局主叫国际号码拦截开关
     */
    public static void init() throws ExecutionException, InterruptedException {

        logger.info("init supportedCodec start");
        String codec = RegistryService.GetEtcdValue(ConstantConf.SUPPORTED_CODEC);
        supportedCodec = codec.split(",");
        logger.info("init supportedCodec success");

        logger.info("init maxSession start");
        maxSession = Integer.parseInt(RegistryService.GetEtcdValue(ConstantConf.MAX_SESSION));
        logger.info("init maxSession success, current maxSession is {}", maxSession);

        logger.info("init audit_strategy_allow start");
        String tmpAuditStrategySwitch = RegistryService.GetEtcdValue(ConstantConf.AUDIT_STRATEGY_ALLOW);
        auditStrategyAllow = StringUtils.isBlank(tmpAuditStrategySwitch) ? 0 : Integer.parseInt(tmpAuditStrategySwitch);
        logger.info("init audit_strategy_allow success, current tmpAuditStrategySwitch: {}, auditStrategyAllow: {}", tmpAuditStrategySwitch, auditStrategyAllow);

        logger.info("init FORCE_TO_GD_ICSCF_CITY start");
        String tmpForceToGdICscfCity = RegistryService.GetEtcdValue(ConstantConf.FORCE_TO_GD_ICSCF_CITY);
        forceToGdICscfCity = StringUtils.isBlank(tmpForceToGdICscfCity) ? new ArrayList<>() : Arrays.asList(tmpForceToGdICscfCity.split(","));
        logger.info("init FORCE_TO_GD_ICSCF_CITY success, current tmpForceToGdICscfCity: {}, forceToGdICscfCity: {}", tmpForceToGdICscfCity, forceToGdICscfCity);


        logger.info("init ZHEJIANG_JILING_IP start");
        String ipString = RegistryService.GetEtcdValue(ConstantConf.ZHEJIANG_JILING_IP);
        ConfigCache.refreshZheJiangJiLingIp(ipString);
        logger.info("init ZHEJIANG_JILING_IP success, current ZHEJIANG_JILING_IP list: {}", ConfigCache.getZjjlIpListSet());


        logger.info("init certification skipped by bind time start");
        String certSkippedByBindTime = RegistryService.GetEtcdValue(ConstantConf.CERT_SKIPPED_BY_BIND_TIME);
        DevHelp.setCertSkippedByBindTime("1".equalsIgnoreCase(certSkippedByBindTime));
        logger.info("init certification controlled by bind time completed, certSkippedByBindTime is {}", DevHelp.isCertSkippedByBindTime());

        logger.info("init db operation reject ratio start");
        String dbOperRejectRatioStr = RegistryService.GetEtcdValue(ConstantConf.DB_OPER_REJECT_RATIO);
        if (StringUtils.isNotBlank(dbOperRejectRatioStr)) {
            DevHelp.setDbOperRejectRatio(Float.parseFloat(dbOperRejectRatioStr));
        }
        logger.info("init db operation reject ratio completed, dbOperRejectRatio is {}", DevHelp.getDbOperRejectRatio());
    }

    private static void initStringEtcdValue(String key, Consumer<String> consumer) throws ExecutionException, InterruptedException {
        logger.info("init {} start", key);
        String value = RegistryService.GetEtcdValue(key);
        if (StringUtils.isNotBlank(value)) {
            consumer.accept(value);
        }
        logger.info("init {} end, current value: {}", key, value);
    }

    public static List<CallerCalleePrefixMap> parseDisplayNoHardCode(String phonePhase) {
        String[] allDisplayMap = phonePhase.split(";");
        List<CallerCalleePrefixMap> result = new ArrayList<>();
        Arrays.stream(allDisplayMap).forEach(item -> {
            String[] split1 = item.split("\\),\\(");
            String caller = split1[0].substring(1);
            String callee = split1[1].substring(0, split1[1].length() - 1);
            CallerCalleePrefixMap map = new CallerCalleePrefixMap();
            map.addAllCaller(caller.split(","));
            map.addAllCallee(callee.split(","));
            result.add(map);
        });
        return result;
    }

    public static List<String> parseCalleeLimitPhonePrefix(String prefixString) {
        String[] prefixMap = prefixString.split(",");
        return Arrays.asList(prefixMap);
    }

    public static void processRouteSipProxyAsIdList(String routeSipProxyAsIdList) {
        if (StringUtils.isNotBlank(routeSipProxyAsIdList)) {
            String[] routeSipProxyAsIdArray = routeSipProxyAsIdList.trim().split(",");
            for (String asIdStr : routeSipProxyAsIdArray) {
                if (asIdStr.contains("-")) {
                    String[] range = asIdStr.split("-");
                    if (range.length == 2 && StringUtils.isNumeric(range[0]) && StringUtils.isNumeric(range[1])) {
                        if (AppConfig.getServerId() >= Integer.parseInt(range[0]) && AppConfig.getServerId() <= Integer.parseInt(range[1])) {
                            isRouteSipProxy = true;
                            return;
                        }
                    } else {
                        logger.error("route_sip_proxy_as_id_list format error!!!!!");
                        // throw new RuntimeException("route_sip_proxy_as_id_list format error!!!!!");
                    }
                } else if (StringUtils.isNumeric(asIdStr)) {
                    if (AppConfig.getServerId() == Integer.parseInt(asIdStr)) {
                        isRouteSipProxy = true;
                        return;
                    }
                } else {
                    logger.error("route_sip_proxy_as_id_list format error!!!!!");
                    // throw new RuntimeException("route_sip_proxy_as_id_list format error!!!!!");
                }
            }
        }
        // 如果本次获取的asId的list中没有本机的serverId则置为false
        isRouteSipProxy = false;
    }

    public static List<TransferLimitDto> parseTransferLimitMap(String prefixString) {
        List<TransferLimitDto> result = new ArrayList<>();
        if (StringUtils.isBlank(prefixString)) {
            logger.info("parseTransferLimitMap with blank input");
            return result;
        }
        String[] prefixArray = prefixString.split(",");
        Arrays.stream(prefixArray).forEach(prefix -> {
            String[] split = prefix.split("-");
            if (split.length != 2 || (!TRANSFER_LIMIT_NUM_KEY.equals(split[0]) && !TRANSFER_LIMIT_PREFIX_KEY.equals(split[0]))) {
                logger.error("parseTransferLimitMap with a error input! prefix:{}", prefix);
                return;
            }
            TransferLimitDto transferLimitDto = TransferLimitDto.instanceOf(split[0], split[1]);
            if (transferLimitDto != null) {
                result.add(transferLimitDto);
            }
        });
        return result;
    }

    public static void setSupportedCodec(String[] supportedCodec) {
        if(supportedCodec.length > 0) {
            DevHelp.supportedCodec = supportedCodec;
        } else {
            logger.error("new supportedCodec is empty!");
        }
    }

    public static String[] getSupportedCodec() {
        return supportedCodec;
    }

    public static void setForceAsr(String forceAsr) {
        DevHelp.forceAsr = forceAsr;
    }

    public static String getForceAsr() {
        return forceAsr;
    }

    public static boolean isNeedForceAsr() {
        return "1".equalsIgnoreCase(forceAsr);
    }

    public static String getForceCodec() {
        return forceCodec;
    }

    public static void setForceCodec(String forceCodec) {
        DevHelp.forceCodec = forceCodec;
    }

    public static String getId() {
        return id;
    }

    public static void setId(String id) {
        DevHelp.id = id;
    }

    public static List<String> getCalleeLimitNumPrefixList() {
        return calleeLimitNumPrefixList;
    }

    public static void setCalleeLimitNumPrefixList(List<String> calleeLimitNumPrefixList) {
        DevHelp.calleeLimitNumPrefixList = calleeLimitNumPrefixList;
    }

    public static List<TransferLimitDto> getTransferBlacklistPrefixList() {
        return transferBlacklistPrefixList;
    }

    public static void setTransferBlacklistPrefixList(List<TransferLimitDto> transferBlacklistPrefixList) {
        DevHelp.transferBlacklistPrefixList = transferBlacklistPrefixList;
    }

    public static List<TransferLimitDto> getTransferWhitelistPrefixList() {
        return transferWhitelistPrefixList;
    }

    public static void setTransferWhitelistPrefixList(List<TransferLimitDto> transferWhitelistPrefixList) {
        DevHelp.transferWhitelistPrefixList = transferWhitelistPrefixList;
    }

    public static boolean isCertSkippedByBindTime() {
        return DevHelp.isCertSkippedByBindTime;
    }

    public static void setCertSkippedByBindTime(boolean isCertSkippedByBindTime) {
        DevHelp.isCertSkippedByBindTime = isCertSkippedByBindTime;
    }

    public static float getDbOperRejectRatio() {
        return DevHelp.dbOperRejectRatio;
    }

    public static void setDbOperRejectRatio(float dbOperRejectRatio) {
        DevHelp.dbOperRejectRatio = dbOperRejectRatio;
    }


}
