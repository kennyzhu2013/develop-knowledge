package com.cmic.start;

import com.cmic.config.RocketMQConfig;
import com.cmic.dao.db.*;
import com.cmic.sip.engine.DtmfEngine;
import com.cmic.GracefulDownHandler;
import com.cmic.config.RedisConfig;
import com.cmic.config.AppConfig;
import com.cmic.recording.MediaManager;
import com.cmic.service.*;
import com.cmic.util.DevHelp;
import com.cmic.util.JedisClientPool;
import com.cmic.util.MyHTTPServer;
import com.common.dao.db.DbManager;
import com.common.dao.db.ResultSetConvertor;
// import com.dcc.config.config.CtitCommon;
import gov.nist.javax.sip.ListeningPointImpl;
import gov.nist.javax.sip.stack.NettyDatagramQueuedMessageDispatch;
import gov.nist.javax.sip.stack.NettyUDPMessageProcessor;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPoolConfig;
import sun.misc.Signal;

import javax.sip.ListeningPoint;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.cli.*;

public class Application {
    private static Logger logger = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        CommandLineParser parser = new DefaultParser();
        Options options = new Options();
        options.addOption(Option.builder("config").hasArg().desc("/home/config/application-config.xml").build());
        options.addOption(Option.builder().longOpt("mediarecordhost").desc("media direct link host").hasArg().build());
        options.addOption(Option.builder().longOpt("force_config").desc("use force config xml").hasArg().build());
        options.addOption(Option.builder().longOpt("delayStart").desc("delay start for milliseconds").hasArg().build());
        final Properties props = System.getProperties();
        props.setProperty("jdk.internal.httpclient.disableHostnameVerification", Boolean.TRUE.toString());
        try {
            /**初始化配置文件**/
            CommandLine line = parser.parse(options, args);
            if (line.hasOption("force_config")) {
                AppConfig.init(line.getOptionValue("force_config"));
            } else if (line.hasOption("config")) {
                AppConfig.init(line.getOptionValue("config"));
            } else {
                AppConfig.init("config/application-config.xml");
            }
            if (line.hasOption("mediarecordhost")) {
                MyRegistryService.mediaRecordHost = line.getOptionValue("mediarecordhost");
                logger.info("Application-main, use mediarecordhost:{}", MyRegistryService.mediaRecordHost);
            }

            if (line.hasOption("delayStart")) {
                long delayStart = Long.parseLong(line.getOptionValue("delayStart"));
                logger.info("Application-main, use delayStart:{}", delayStart);
                Thread.sleep(delayStart);
            }
            /** 首先初始化Sip协议栈 **/
            initSipStack();
            // 普通模式加载数据库、ETCD、MQ等依赖
            if(AppConfig.getStartMode() == 0) {
                /**  初始化数据库  **/
                initDb();
                /**  初始化公司集合 **/
                AuthConfigService.getInstance().init();
                /** 初始化etcd服务发现 **/
                initEtcd();
                /** 初始化RocketMQ **/
                initRocketMQ();
            } else {
                logger.warn("Application startMode:{} skip middleware initialization", AppConfig.getStartMode());
            }
            DevHelp.initStartMode = AppConfig.getStartMode();
            DevHelp.currentMode = AppConfig.getStartMode();
            /**初始化BillIdservice**/
            BillIdService.getInstance().init(AppConfig.getLocationId(), AppConfig.getServerId());
            /**初始化对外http服务**/
            MyHTTPServer httpServer = new MyHTTPServer(AppConfig.getHttpIp(), AppConfig.getHttpPort(), 5);

            /**初始化媒体回调URL**/
            MediaManager.init("http://" + AppConfig.getHttpIp() + ":" + AppConfig.getHttpPort());
            /** 如需优雅停机，监听：kill -2 **/
            Signal.handle(new Signal("INT"), new GracefulDownHandler());
            recordVersion();
            auditSipEngine();
        } catch (Exception e) {
            logger.error("", e);
            logger.info("StartFail");
            System.exit(1);
        }
    }

    private static void recordVersion() throws IOException {
        String version = getVersion();
        if (StringUtils.isNotBlank(version)) {
            version = ", version:" + version;
            XmodePromExporterService.getInstance().setXdtmfVersion(version, AppConfig.getAsListen());
        }
        logger.info("StartSuccess {}", version);
    }

    private static void auditSipEngine() throws IOException {
        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            Iterator listeningPoints = DtmfEngine.getInstance().sipStack.getListeningPoints();
            while (listeningPoints.hasNext()) {
                ListeningPoint lp = (ListeningPoint) listeningPoints.next();
                ListeningPointImpl lpi = (ListeningPointImpl) lp;
                NettyUDPMessageProcessor messageProcessor = (NettyUDPMessageProcessor) lpi.getMessageProcessor();
                BlockingQueue<NettyDatagramQueuedMessageDispatch> messageQueue = messageProcessor.messageQueue;
                int size = messageQueue.size();
                XmodePromExporterService.getInstance().setSipMessageQueueSize(AppConfig.getHttpIp(), size);
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    private static String getVersion() throws IOException {
        File file = new File("version");
        if (file.exists()) {
            BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
            return bufferedReader.readLine();
        }
        return "";
    }

    private static void initSipStack() throws Exception {
        logger.info("初始化Sip协议栈");
        DtmfEngine.getInstance().init();
        logger.info("初始化Sip协议栈完成");
    }

    private static void initDb() {

        logger.info("初始化数据库");
        HashMap<String, String> config = new HashMap<>();
        // 企业配置等数据
        System.out.println("===========" + AppConfig.getConfDbPath());
        config.put(UserQcDB.DB_ALIAS_NAME, AppConfig.getConfDbPath());
        // 话单数据
        System.out.println("===========" + AppConfig.getBillConfigPath());
        config.put(BillDB.DB_ALIAS_NAME, AppConfig.getBillConfigPath());
        DbManager.getInstance().init(config);
        String initSql = "select 1";
        int count = BillDB.getInstance().executeQueryObject(initSql, ResultSetConvertor.INTEGER);
        logger.info("InitSql BillDB success {}", count);
        MultiDBService.addBillDB(BillDB.getInstance());
        DbAsyncService.getInstance().init();
        PhoneSegmentCacheService.init();
        ChinaMobileIcscfCacheService.init();
    }

    private static void initEtcd() throws Exception {
        logger.info("初始化etcd开始");
        MyRegistryService.getInstance().init();
        logger.info("初始化etcd完成");
    }

    public static synchronized void initRedis() throws Exception {
        RedisConfig.init("config/redis.xml");
        JedisPoolConfig jedisPoolConfig = RedisConfig.getJedisPoolConfig();
        String host = RedisConfig.getHost();
        int port = RedisConfig.getPort();
        int timeout_mill = RedisConfig.getTimeout_mill();
        String password = RedisConfig.getPassword();
        logger.info("初始化redis开始,jedisPoolConfig:{}, host:{}, port:{}, timeout_mill:{}", jedisPoolConfig, host, port, timeout_mill);
        JedisClientPool.init(jedisPoolConfig, host, port, timeout_mill, password);
        logger.info("初始化redis完成");
    }

    public static void initRocketMQ() throws Exception {
        RocketMQConfig rocketMQConfig = new RocketMQConfig();
        rocketMQConfig.init("config/rocketmq.xml");
        /** 初始化话单MQ队列 **/
        BillRocketMQService.getInstance().init(rocketMQConfig, AppConfig.getPublishMQThreadPoolCfg());
        /** 初始化事件MQ队列 **/
        // EventRocketMQService.getInstance().init(rocketMQConfig, XdtmfAppConfig.getPublishMQThreadPoolCfg());
    }


}
