package com.cmic.utils;

import com.cmic.metrics.PusherMetrics;
import com.discovery.RegistryService;
import io.etcd.jetcd.watch.WatchEvent;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
@Slf4j
public class Parameters {

    @Value("${parameters-config.location}")
    public int location;
    @Value("${parameters-config.cloud}")
    public int cloud;
    @Value("${parameters-config.etcd}")
    public String etcd;
    @Value("${server.address}")
    public String ip;
    @Value("${server.port}")
    public int port;
    @Value("${parameters-config.second-reach-prod}")
    public String secondReachProd;
    @Value("${parameters-config.second-reach-test}")
    public String secondReachTest;
    @Value("${parameters-config.second-reach-secretary-prod}")
    public String secondReachSecretaryProd;
    @Value("${parameters-config.second-reach-secretary-test}")
    public String secondReachSecretaryTest;
    @Value("${parameters-config.cluster-name}")
    public String clusterName;
    @Value("${parameters-config.version}")
    public String version;
    @Value("${parameters-config.httpproxy-name}")
    public String HttpproxyServiceName;
    @Value("${parameters-config.debug-record-host}")
    public String debugRecordHost;
    @Value("${parameters-config.max-concurrency}")
    public int maxConcurrency;
    @Value("${parameters-config.billConsumerCount}")
    public int billConsumerCount;
    @Value("${parameters-config.recordConsumerCount}")
    public int recordConsumerCount;
    @Value("${parameters-config.inspectionConsumerCount}")
    public int inspectionConsumerCount;
    @Value("${parameters-config.delayRecordConsumerCount}")
    public int delayRecordConsumerCount;

    @Autowired
    private CompanyTabManager companyTabManager;
    @Autowired
    private CustomerTabManager customerTabManager;
    @Autowired
    private FileDownHostManager fileDownHostManager;

    public Set<String> inernalPlatformSet = new HashSet<>();


    @Autowired
    private PusherMetrics pusherMetrics;


    private final String MyServiceName = "com.cmic.vi.pusher";

    public String printString() {
        return "\nParameters init................" +
                "\nlocation = " + location +
                "\netcd = " + etcd +
                "\n..............................";
    }


    @PostConstruct
    public void parameterInit() {

        log.info(printString());

        //初始化数据源
        try {
            pusherMetrics.unUseHistoryTableCount.set(7);
            // 初始化与Etcd之间的连接
            // RegistryService.Init(ParamsUtil.etcdIps);
            RegistryService.Init(clusterName, this.etcd);
            RegistryService.Register(MyServiceName, this.ip, this.port);
            // 获取服务列表，并监听服务列表的变化
            RegistryService.WatchService(HttpproxyServiceName);
            RegistryService.WatchEtcdKey("/config/version", (event) -> {
                if (event.getEventType() == WatchEvent.EventType.PUT) {
                    log.info("cache reload");
                    companyTabManager.reload();
                    customerTabManager.reload();
                    fileDownHostManager.reload();
                }
                return true;
            });
        } catch (Exception e) {
            log.error("StartFail", e);
            System.exit(1);
        }
    }
}
