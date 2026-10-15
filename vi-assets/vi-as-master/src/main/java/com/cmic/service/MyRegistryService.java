package com.cmic.service;

import com.cmic.cache.ConfigCache;
import com.cmic.config.AbnormalBillAnalyserConfig;
import com.cmic.config.ConstantConf;
import com.cmic.config.AppConfig;
import com.cmic.util.DevHelp;
import com.cmic.util.DtmfLogger;
import com.discovery.RegistryException;
import com.discovery.RegistryService;
import com.discovery.ServiceNode;
import com.google.common.base.Charsets;
import com.util.IPPort;
import io.etcd.jetcd.watch.WatchEvent;
import lombok.Getter;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * @author XXXX
 * @date 2023/07/21
 */
public class MyRegistryService {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    @Getter
    public static MyRegistryService instance = new MyRegistryService();
    public static String mediaRecordHost;

    private MyRegistryService() {
    }

    public void init() throws RegistryException, ExecutionException, InterruptedException {
        RegistryService.Init(AppConfig.getEtcdNodeName(), AppConfig.getEtcd());
        RegistryService.WatchService(AppConfig.getHttpProxyName());
        RegistryService.WatchService(ConstantConf.RECORD_SERVICE_NAME);
        RegistryService.WatchService(ConstantConf.SIP_PROXY_SERVICE_NAME);
        RegistryService.WatchService(ConstantConf.GO_STRATEGY_SERVICE_NAME);
        RegistryService.WatchEtcdKey(ConstantConf.ETCD_RELOAD_CONF_KEY, (event) -> {
            if (event.getEventType() == WatchEvent.EventType.PUT) {
                logger.info("Corporate Information reload start");
                AuthConfigService.getInstance().reloadBizInfo();
                logger.info("Corporate Information reload complete");

            }
            return true;
        });

        RegistryService.WatchEtcdKey(ConstantConf.ETCD_RELOAD_NUM_CONF_KEY, (event) -> {
            if (event.getEventType() == WatchEvent.EventType.PUT) {
                logger.info("num conf reload start");
                AuthConfigService.getInstance().reloadNumConf();
                logger.info("num conf reload complete");
            }
            return true;
        });

        RegistryService.WatchEtcdKey(ConstantConf.ETCD_RELOAD_CHINA_MOBILE_CSCF_VERSION, (event) -> {
            if (event.getEventType() == WatchEvent.EventType.PUT) {
                logger.info("ChinaMobileIcscfCache Information reload start");
                ChinaMobileIcscfCacheService.reload();
                logger.info("ChinaMobileIcscfCache Information reload complete");
            }
            return true;
        });

        RegistryService.WatchEtcdKey(ConstantConf.ETCD_RELOAD_PHONE_SEGMENT_VERSION, (event) -> {
            if (event.getEventType() == WatchEvent.EventType.PUT) {
                logger.info("phone_segment reload start");
                PhoneSegmentCacheService.reload();
                logger.info("phone_segment reload complete");
            }
            return true;
        });
        // 加载是否开启主叫拦截的开关标识
        DevHelp.init();

        RegistryService.WatchEtcdKey(ConstantConf.SUPPORTED_CODEC, (event) -> {
                    if (event.getEventType() == WatchEvent.EventType.PUT) {
                        logger.info("supported codec start");
                        String codec = event.getKeyValue().getValue().toString(Charsets.UTF_8);
                        String[] supportedCodec = codec.toLowerCase().split(",");
                        DevHelp.setSupportedCodec(supportedCodec);
                        logger.info("supported codec complete， supportedCodec：{}", Arrays.toString(supportedCodec));
                    }
                    return true;
                }
        );

        RegistryService.WatchEtcdKey(ConstantConf.MAX_SESSION, (event) -> {
                    if (event.getEventType() == WatchEvent.EventType.PUT) {
                        logger.info("max_session reload start");
                        DevHelp.maxSession = Integer.parseInt(event.getKeyValue().getValue().toString(Charsets.UTF_8));
                        logger.info("max_session reload complete , current max_session is {}", DevHelp.maxSession);
                    }
                    return true;
                }
        );

        RegistryService.WatchEtcdKey(ConstantConf.CALLEE_LIMIT_NUM_PREFIX, (event) -> {
                    if (event.getEventType() == WatchEvent.EventType.PUT) {
                        logger.info("callee_limit_num_prefix reload start");
                        String phoneSegment = event.getKeyValue().getValue().toString(Charsets.UTF_8);
                        DevHelp.setCalleeLimitNumPrefixList(DevHelp.parseCalleeLimitPhonePrefix(phoneSegment));
                        logger.info("callee_limit_num_prefix reload complete , current callee_limit_num_prefix is {}", DevHelp.getCalleeLimitNumPrefixList());
                    }
                    return true;
                }
        );


        RegistryService.WatchEtcdPrefixKey(ConstantConf.ETCD_ANALYSE_ABNORMAL_BILL_PREFIX, (event) -> {
            if (event.getEventType() == WatchEvent.EventType.PUT) {
                logger.info("analyse abnormal bill case1 switch reload start");
                String key = event.getKeyValue().getKey().toString(Charsets.UTF_8);
                String value = event.getKeyValue().getValue().toString(Charsets.UTF_8);
                if (key.endsWith(ConstantConf.ETCD_ANALYSE_ABNORMAL_BILL_CASE1_SWITCH)) {
                    AbnormalBillAnalyserConfig.case1Switch("1".equalsIgnoreCase(value));
                } else if (key.endsWith(ConstantConf.ETCD_ANALYSE_ABNORMAL_BILL_CASE2_SWITCH)) {
                    AbnormalBillAnalyserConfig.case2Switch("1".equalsIgnoreCase(value));
                } else if (key.endsWith(ConstantConf.ETCD_ANALYSE_ABNORMAL_BILL_CASE3_SWITCH)) {
                    AbnormalBillAnalyserConfig.case3Switch("1".equalsIgnoreCase(value));
                } else if (key.endsWith(ConstantConf.ETCD_ANALYSE_ABNORMAL_BILL_CASE4_SWITCH)) {
                    AbnormalBillAnalyserConfig.case4Switch("1".equalsIgnoreCase(value));
                } else if (key.endsWith(ConstantConf.ETCD_ANALYSE_ABNORMAL_BILL_CASE5_SWITCH)) {
                    AbnormalBillAnalyserConfig.case5Switch("1".equalsIgnoreCase(value));
                } else if (key.endsWith(ConstantConf.ETCD_ANALYSE_ABNORMAL_BILL_CASE6_SWITCH)) {
                    AbnormalBillAnalyserConfig.case6Switch("1".equalsIgnoreCase(value));
                } else if (key.endsWith(ConstantConf.ETCD_ANALYSE_ABNORMAL_BILL_CASE7_SWITCH)) {
                    AbnormalBillAnalyserConfig.case7Switch("1".equalsIgnoreCase(value));
                }
                logger.info("analyse abnormal bill switch reload completed, case1:{}, case2:{}" +
                                ", case3:{}, case4:{}, case5:{}, case6:{}, case7:{}",
                        AbnormalBillAnalyserConfig.case1Switch(),
                        AbnormalBillAnalyserConfig.case2Switch(),
                        AbnormalBillAnalyserConfig.case3Switch(),
                        AbnormalBillAnalyserConfig.case4Switch(),
                        AbnormalBillAnalyserConfig.case5Switch(),
                        AbnormalBillAnalyserConfig.case6Switch(),
                        AbnormalBillAnalyserConfig.case7Switch());
            }
            return true;
        });

        RegistryService.WatchEtcdKey(ConstantConf.AUDIT_STRATEGY_ALLOW, (event) -> {
                    if (event.getEventType() == WatchEvent.EventType.PUT) {
                        logger.info("audit_strategy_allow reload start");
                        String tmpAuditStrategySwitch = event.getKeyValue().getValue().toString(Charsets.UTF_8);
                        DevHelp.auditStrategyAllow = StringUtils.isBlank(tmpAuditStrategySwitch) ? 0 : Integer.parseInt(tmpAuditStrategySwitch);
                        logger.info("audit_strategy_allow reload completed, audit_strategy_allow is {}", DevHelp.auditStrategyAllow);
                    }
                    return true;
                }
        );

        RegistryService.WatchEtcdKey(ConstantConf.FORCE_TO_GD_ICSCF_CITY, (event) -> {
                    if (event.getEventType() == WatchEvent.EventType.PUT) {
                        logger.info("FORCE_TO_GD_ICSCF_CITY reload start");
                        String tmpForceToGDIcscfCity = event.getKeyValue().getValue().toString(Charsets.UTF_8);
                        DevHelp.forceToGdICscfCity = StringUtils.isBlank(tmpForceToGDIcscfCity) ? new ArrayList<>() : Arrays.asList(tmpForceToGDIcscfCity.split(","));
                        logger.info("FORCE_TO_GD_ICSCF_CITY reload completed, forceToGdICscfCity is {}", DevHelp.forceToGdICscfCity);
                    }
                    return true;
                }
        );

        RegistryService.WatchEtcdKey(ConstantConf.ZHEJIANG_JILING_IP, (event) -> {
                    if (event.getEventType() == WatchEvent.EventType.PUT) {
                        logger.info("ZheJiang JiLing IP reload start");
                        String ipString = event.getKeyValue().getValue().toString(Charsets.UTF_8);
                        ConfigCache.refreshZheJiangJiLingIp(ipString);
                        logger.info("ZheJiang JiLing IP reload completed, ZheJiang JiLing IP is {}", ConfigCache.getZjjlIpListSet());
                    }
                    return true;
                }
        );

        RegistryService.WatchEtcdKey(ConstantConf.PUBLISH_ROCKET_MQ_THREAD_POOL, (event) -> {
                    if (event.getEventType() == WatchEvent.EventType.PUT) {
                        logger.info("publish_rocketmq_thread_pool reload start");
                        String value = event.getKeyValue().getValue().toString(Charsets.UTF_8);
                        DevHelp.publishMQThreadPool = "1".equalsIgnoreCase(value);
                        logger.info("publish_rocketmq_thread_pool reload completed, publish_rocketmq_thread_pool is {}", DevHelp.publishMQThreadPool);
                    }
                    return true;
                }
        );

        RegistryService.WatchEtcdKey(ConstantConf.DB_OPER_REJECT_RATIO, (event) -> {
                    if (event.getEventType() == WatchEvent.EventType.PUT) {
                        logger.info("DB_OPER_REJECT_RATIO changed");
                        String dbOperRejectRatioStr = event.getKeyValue().getValue().toString(Charsets.UTF_8);
                        if (StringUtils.isNotBlank(dbOperRejectRatioStr)) {
                            DevHelp.setDbOperRejectRatio(Float.valueOf(dbOperRejectRatioStr));
                            DbAsyncService.getInstance().refreshMaxConcurrency(DevHelp.getDbOperRejectRatio());
                        }
                        logger.info("new DB_OPER_REJECT_RATIO is {} ", DevHelp.getDbOperRejectRatio());
                    }
                    return true;
                }
        );
        RegistryService.WatchEtcdKey(ConstantConf.CHINAMOBILE_AREACODE, (event) -> {
                    if (event.getEventType() == WatchEvent.EventType.PUT) {
                        logger.info("CHINAMOBILE_AREACODE changed");
                        ChinaMobileIcscfCacheService.reload();
                        logger.info("CHINAMOBILE_AREACODE reload end");
                    }
                    return true;
                }
        );

    }

    public String getHttpProxyHost() {
        return RegistryService.GetServiceNode(AppConfig.getHttpProxyName());
    }

    public ServiceNode getRecordHost() {
        ServiceNode etcdServiceNode = RegistryService.getServiceNode(ConstantConf.RECORD_SERVICE_NAME);
        if (StringUtils.isBlank(MyRegistryService.mediaRecordHost)) {
            return etcdServiceNode;
        }
        String[] split = MyRegistryService.mediaRecordHost.split(":");
        if (split.length < 2) {
            logger.error("test mediaRecordHost error:{}", MyRegistryService.mediaRecordHost);
        }
        etcdServiceNode = new ServiceNode();
        etcdServiceNode.setId("9999");
        etcdServiceNode.setHost(split[0]);
        etcdServiceNode.setPort(split[1]);
        return etcdServiceNode;
    }


    public IPPort getSipProxyHost() {
        String host = RegistryService.GetServiceNode(ConstantConf.SIP_PROXY_SERVICE_NAME, true);
        DtmfLogger.info("***************** getSipProxyHost host *************:{}", host);
        if (StringUtils.isNotBlank(host) && host.split(":").length > 1) {
            String[] hostParts = host.split(":");
            DtmfLogger.info("***************** getSipProxyHost hostParts[0]:{},hostParts[1]:{}", hostParts[0], hostParts[1]);
            return new IPPort(hostParts[0], Integer.parseInt(hostParts[1]));
        }
        return null;
    }

}
