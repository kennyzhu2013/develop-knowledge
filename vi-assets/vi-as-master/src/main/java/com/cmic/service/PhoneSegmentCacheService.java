package com.cmic.service;

import com.cmic.dao.db.UserQcDB;
import com.cmic.util.SipUtil;
import com.common.dao.db.ResultSetConvertor;
import com.dcc.config.dao.DccConfigDb;
import lombok.Data;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * 号段服务
 * Author: XH
 * Date: 2026/4/20
 */
public class PhoneSegmentCacheService {
    private static Map<String, PhoneSegment> mss = new HashMap<>();
    private static HashMap<String, Integer> areaCodeMap = new HashMap<>();
    private static HashMap<Integer, String>  provinceIdNameMap = new HashMap<>();
    public static int BATCH_SIZE = 30000;
    private static final Logger logger = LoggerFactory.getLogger(PhoneSegmentCacheService.class);


    public static void init() {
        reload();
    }

    private static ArrayList<PhoneSegment> query(String query, Object... params) {
        return UserQcDB.getInstance().executeQueryObjectList(query, params, PhoneSegment.BUILDER);
    }

    public static void reload() {
        long maxId = 0;
        logger.info("[PhoneSegment] start fetch, BATCH_SIZE:{}", BATCH_SIZE);
        while (true) {
            ArrayList<PhoneSegment> phoneSegments = query("select id,province,center_area_code,area_code,operator,number_segment,province_id from phone_segment_area_code_info where id > ? order by id asc limit ?", maxId, BATCH_SIZE);
            for (PhoneSegment r : phoneSegments) {
                String phone = r.getNumberSegment();
                mss.put(phone, r);
                areaCodeMap.put(r.getAreaCode(), r.getProvinceId());
                provinceIdNameMap.put(r.getProvinceId(), r.getProvince());
                if (r.getId() > maxId) {
                    maxId = r.getId();
                }
            }
            logger.info("[PhoneSegment] fetch BATCH_SIZE:{}, maxId:{}", BATCH_SIZE, maxId);
            if (phoneSegments.size() < BATCH_SIZE) {
                break;
            }
        }
        logger.info("[PhoneSegment] fetch all: maxId:{}", maxId);
    }

    @Data
    public static class PhoneSegment {
        private Long id;
        private String province;
        private Integer provinceId;
        private String centerAreaCode;
        private String areaCode;
        private Integer operator;
        private String numberSegment;

        public static final ResultSetConvertor<PhoneSegment> BUILDER = new ResultSetConvertor<>() {
            @Override
            public PhoneSegment convert(ResultSet rs) throws SQLException {
                PhoneSegment phoneSegment = new PhoneSegment();
                phoneSegment.setId(rs.getLong("id"));
                phoneSegment.setProvince(rs.getString("province"));
                phoneSegment.setProvinceId(rs.getInt("province_id"));
                phoneSegment.setCenterAreaCode(rs.getString("center_area_code"));
                phoneSegment.setAreaCode(rs.getString("area_code"));
                phoneSegment.setOperator(Integer.valueOf(rs.getString("operator")));
                phoneSegment.setNumberSegment(rs.getString("number_segment"));
                return phoneSegment;
            }
        };
    }

    public static boolean isAreaCodeValid(String areaCode) {
        return areaCodeMap.containsKey(areaCode);
    }

    public static String getAreaCodeFromPhoneSegment(String phoneSegment) {
        PhoneSegment phoneSegment1 = mss.get(phoneSegment);
        return phoneSegment1.getCenterAreaCode();
    }

    public static Integer getProvinceIdFromAreaCode(String areaCode) {
        if(areaCodeMap.get(areaCode) == null) {
            logger.warn("[PhoneSegment] fetch all: maxId:{}", areaCode);
            return 0;
        }
        return areaCodeMap.get(areaCode);
    }

    public static String getProvinceNameById(int provinceId) {
        return provinceIdNameMap.getOrDefault(provinceId, "");

    }

    /**
     * 截取号码前7位，匹配号段表缓存信息，用于定位号码对应的归属信息
     * @param phone 需要查询的号码
     * @return 包含号段和其归属信息的PhoneSegment对象
     */
    public static PhoneSegment getSegmentDtoFromPhone(String phone) {
        if (StringUtils.isBlank(phone)) {
            return null;
        }
        phone = SipUtil.GetPhoneNumber(phone);
        if (phone.length() < 7) {
            return null;
        }
        String phonePre7 = phone.substring(0, 7);
        if (!mss.containsKey(phonePre7)) {
            return null;
        }
        return mss.get(phonePre7);
    }

}
