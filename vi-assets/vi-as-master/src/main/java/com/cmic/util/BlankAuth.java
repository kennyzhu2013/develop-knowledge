package com.cmic.util;

import com.cmic.dao.BizNumAuthDo;
import com.cmic.dao.ChannelDo;
import com.cmic.dao.CompanyDo;
import com.cmic.dao.CustomerDo;

import java.sql.Timestamp;

/**
 * 空的企业信息
 * <p>
 * Author: luohuanfa
 * Date: 2026/6/22
 */
public class BlankAuth {
    private static Timestamp now = new Timestamp(System.currentTimeMillis());
    public static CompanyDo BLANK_COMPANY = new CompanyDo(0, "null", "null", "null", "null", "null", 0, 1, "null", "null", "null", now, now);
    public static CustomerDo BLANK_CUSTOMER = new CustomerDo(0, "null", "null", "null", 0, 1, 0, "null", "null", "null", now, now, 0, "null", "null", 0, "null", 0, 0, 0, 0, 0, 0, now);
    public static ChannelDo BLANK_CHANNEL = new ChannelDo(0, "null", "null", "null", 0, "null", "null", "null", now, now);
}
