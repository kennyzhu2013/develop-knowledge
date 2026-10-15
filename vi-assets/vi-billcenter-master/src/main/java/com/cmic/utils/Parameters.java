package com.cmic.utils;

import com.discovery.RegistryService;
import io.etcd.jetcd.watch.WatchEvent;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component

public class Parameters {
    @Value("${parameters-config.location}")
    public int location;
    @Value("${parameters-config.etcd}")
    public String etcd;
    @Value("${parameters-config.version}")
    public String version;
    @Value("${server.address}")
    public String ip;
    @Value("${server.port}")
    public int port;
    @Value("${parameters-config.cluster-name}")
    public String clusterName;
    @Value("${parameters-config.qc-server-create-url}")
    public String qcServerCreateUrl;


    @Value("${parameters-config.httpproxy-name}")
    public String httpProxyServiceName;

    @Value("${parameters-config.billConsumerCount}")
    public int billConsumerCount;
    @Value("${parameters-config.recordConsumerCount}")
    public int recordConsumerCount;
    @Value("${parameters-config.delayRecordConsumerCount}")
    public int delayRecordConsumerCount;



    private final String MyServiceName = "com.cmic.vi.billcenter";


    @Autowired
    private CompanyTabManager companyTabManager;
    @Autowired
    private CustomerTabManager customerTabManager;
    @Autowired
    private EnterpriseSceneManager enterpriseSceneManager;
    @Autowired
    private FileDownHostManager fileDownHostManager;

    public String printString() {
        return "\n=============================================================================" +
                "\nParameters init:" +
                "\nlocation = " + location +
                "\netcd = " + etcd +
                "\nip = " + ip +
                "\nport = " + port +
                "\n=============================================================================";
    }

    @PostConstruct
    public void applicationInit() {
        log.info(printString());
        //初始化数据源
        try {
            companyTabManager.init();
            customerTabManager.init();
            enterpriseSceneManager.init();
            fileDownHostManager.init();

            // 初始化与Etcd之间的连接
            RegistryService.Init(clusterName, this.etcd);
            // 暴露自己提供的服务，注册到服务中心
            RegistryService.Register(MyServiceName, this.ip, this.port);
            // 获取服务列表，并监听服务列表的变化
            RegistryService.WatchService(httpProxyServiceName);

            RegistryService.WatchEtcdKey("/config/version", (event) -> {
                if (event.getEventType() == WatchEvent.EventType.PUT) {
                    log.info("cache reload");
                    companyTabManager.reload();
                    customerTabManager.reload();
                    enterpriseSceneManager.reload();
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
