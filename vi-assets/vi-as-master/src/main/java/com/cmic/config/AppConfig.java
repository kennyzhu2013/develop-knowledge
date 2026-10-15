package com.cmic.config;

import lombok.Getter;
import org.apache.commons.lang.StringUtils;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppConfig {
    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);
    @Getter
    private static String asListen = "127.0.0.1:5060";
    @Getter
    private static int locationId = 1;
    @Getter
    private static String billConfigPath = "vi_bill.yaml";
    @Getter
    private static String asStackFilePath = "sip-stack-call.properties";
    @Getter
    private static String etcd = "";
    @Getter
    private static boolean billTrace;
    @Getter
    private static String confDbPath = "vi_conf.yaml";
    @Getter
    private static int serverId;
    @Getter
    private static int httpPort;
    @Getter
    private static String httpIp;
    @Getter
    private static String etcdNodeName = "";
    @Getter
    private static final ThreadPoolConfig publishMQThreadPoolCfg = new ThreadPoolConfig(16, 10000);
    @Getter
    private static String SLBAddress = "";
    private static Set<Integer> billCalleeFirstQueryFallbackCustomer = new HashSet<>();

    @Getter
    private static int proxyMode;
    @Getter
    private static String httpProxyName;
    @Getter
    private static int startMode;

    public static void init(String appConfigFile) throws Exception {
        SAXReader reader = new SAXReader();
        Document document = reader.read(new File(appConfigFile));
        @SuppressWarnings("unchecked")
        List<Element> listElement = document.getRootElement().elements();//所有一级子节点的list
        for (Element e : listElement) {//遍历所有一级子节点
            System.out.println("当前节点名称：" + e.getName());//当前节点名称
            System.out.println("当前节点的内容：" + e.getTextTrim());//当前节点名称
            switch (e.getName()) {
                case "asListen":
                    asListen = e.getTextTrim();
                    break;
                case "location":
                    locationId = Integer.parseInt(e.getTextTrim());
                    break;
                case "billConfigPath":
                    billConfigPath = e.getTextTrim();
                    break;
                case "etcd":
                    etcd = e.getTextTrim();
                    break;
                case "asStackFilePath":
                    asStackFilePath = e.getTextTrim();
                    break;
                case "billTrace":
                    billTrace = Boolean.parseBoolean(e.getTextTrim());
                    break;
                case "confDbConfigPath":
                    confDbPath = e.getTextTrim();
                    break;
                case "serverId":
                    serverId = Integer.parseInt(e.getTextTrim());
                    break;
                case "httpIp":
                    httpIp = e.getTextTrim();
                    break;
                case "httpPort":
                    httpPort = Integer.parseInt(e.getTextTrim());
                    break;
                case "etcdNodeName":
                    etcdNodeName = e.getTextTrim();
                    break;
                case "publishMQThreadPoolSize":
                    publishMQThreadPoolCfg.setPoolSize(Integer.parseInt(e.getTextTrim()));
                    break;
                case "publishMQThreadPoolQueueSize":
                    publishMQThreadPoolCfg.setQueueSize(Integer.parseInt(e.getTextTrim()));
                    break;
                case "SLBAddress":
                    SLBAddress = e.getTextTrim();
                    break;
                case "proxyMode":
                    proxyMode = Integer.parseInt(e.getTextTrim());
                    break;
                case "httpProxyName":
                    httpProxyName = e.getTextTrim();
                    break;
                case "startMode":
                    startMode = Integer.parseInt(e.getTextTrim());
                    break;
                default:
                    logger.error("unknown key:value {}:{}", e.getName(), e.getTextTrim());
            }
        }


    }


    public static boolean isInProxyMode(){
        return proxyMode == 1;
    }

}
