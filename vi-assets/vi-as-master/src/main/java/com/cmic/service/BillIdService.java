package com.cmic.service;

import com.util.SnowFlake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BillIdService {
    private static final BillIdService instance = new BillIdService();
    private Logger logger = LoggerFactory.getLogger(getClass());

    private SnowFlake snowFlake16;
    private SnowFlake snowFlake18;

    private static final int BILL_ID_LENGTH = 32;

    public static BillIdService getInstance() {
        return instance;
    }

    public void init(int datacenterId, int machineId) {
        boolean init16Ok = false;
        try {
            snowFlake16 = new SnowFlake(datacenterId, machineId, false);
            init16Ok = true;
        } catch (Exception e) {
            // 静默
        }
        snowFlake18 = new SnowFlake(datacenterId, machineId, true);
        logger.info("billId Service init success! 16:{}, 18:true", init16Ok);
    }

    /**
     * 生成新话单id，通过话单id模式选取
     *
     * @param use16mode 话单id模式，是否使用16位10进制数的模式
     * @return 新话单id
     */
    public long nextId(boolean use16mode) {
        if (snowFlake16 == null && use16mode) {
            long nextId = snowFlake18.nextId();
            logger.error("{} ,billId try to use 16-mode but impossible", nextId);
            return nextId;
        }
        return use16mode ? snowFlake16.nextId() : snowFlake18.nextId();
    }

    /**
     * 新话单id，默认700开头的32位字符串
     *
     * @return 新话单id
     */
    public String nextId() {
        long l = snowFlake18.nextId();
        return String.valueOf(l);
        // int paddingLength = BILL_ID_LENGTH - 1 - s.length();
        // // paddingLength==0时也需要补首位，因此只有<0时直接返回
        // if (paddingLength < 0) {
        //     return s;
        // }
        // StringBuilder sb = new StringBuilder();
        // sb.append('7');
        // for (int i = 0; i < paddingLength; ++i) {
        //     sb.append('0');
        // }
        // sb.append(s);
        // return sb.toString();
    }
}
