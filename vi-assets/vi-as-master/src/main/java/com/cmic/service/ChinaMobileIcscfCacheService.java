package com.cmic.service;

import com.cmic.dao.UserQcDao;
import com.common.dao.db.ResultSetConvertor;
import com.dcc.config.dao.DccConfigDb;
import lombok.Data;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * 号段服务
 * <p>
 * Author: luohuanfa
 * Date: 2025/8/6
 */
public class ChinaMobileIcscfCacheService {
    private static Map<String, String> cscfCenterAreaCode = new HashMap<>();
    private static Map<String, String> cscfIpProvince = new HashMap<>();
    private static Map<String, String> provinceAbbrAreaCode = new HashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(ChinaMobileIcscfCacheService.class);

    static {
        provinceAbbrAreaCode.put("bj", "10");
        provinceAbbrAreaCode.put("tj", "22");
        provinceAbbrAreaCode.put("sh", "21");
        provinceAbbrAreaCode.put("cq", "23");
        provinceAbbrAreaCode.put("he", "311");
        provinceAbbrAreaCode.put("sx", "351");
        provinceAbbrAreaCode.put("nm", "471");
        provinceAbbrAreaCode.put("ln", "24");
        provinceAbbrAreaCode.put("jl", "431");
        provinceAbbrAreaCode.put("hl", "451");
        provinceAbbrAreaCode.put("js", "25");
        provinceAbbrAreaCode.put("zj", "571");
        provinceAbbrAreaCode.put("ah", "551");
        provinceAbbrAreaCode.put("fj", "591");
        provinceAbbrAreaCode.put("jx", "791");
        provinceAbbrAreaCode.put("sd", "531");
        provinceAbbrAreaCode.put("ha", "371");
        provinceAbbrAreaCode.put("hb", "27");
        provinceAbbrAreaCode.put("hn", "731");
        provinceAbbrAreaCode.put("gd", "20");
        provinceAbbrAreaCode.put("gx", "771");
        provinceAbbrAreaCode.put("hi", "898");
        provinceAbbrAreaCode.put("sc", "28");
        provinceAbbrAreaCode.put("gz", "851");
        provinceAbbrAreaCode.put("yn", "871");
        provinceAbbrAreaCode.put("xz", "891");
        provinceAbbrAreaCode.put("sn", "29");
        provinceAbbrAreaCode.put("gs", "931");
        provinceAbbrAreaCode.put("qh", "971");
        provinceAbbrAreaCode.put("nx", "951");
        provinceAbbrAreaCode.put("xj", "991");
    }

    public static void init() {
        reload();
    }

    private static ArrayList<ChinaMobileICSCF> query(String query, Object... params) {
        return DccConfigDb.getInstance().executeQueryObjectList(query, params, ChinaMobileICSCF.BUILDER);
    }

    public static void reload() {
        logger.info("[ChinaMobileIcscf] start fetch");
        ArrayList<ChinaMobileICSCF> chinaMobileICSCF = UserQcDao.getInstance().selectAllChinaMobileICSCF();
        for (ChinaMobileICSCF r : chinaMobileICSCF) {
            String centerAreaCode = r.getCenterAreaCode();
            String ip = r.getIp();
            String province = r.getProvince();
            cscfCenterAreaCode.put(ip, centerAreaCode);
            cscfIpProvince.put(ip, province);
        }
        logger.info("[ChinaMobileIcscf] fetch all: {}", chinaMobileICSCF.size());
    }

    @Data
    public static class ChinaMobileICSCF {
        private String centerAreaCode;
        private String ip;
        private String province;

        public static final ResultSetConvertor<ChinaMobileICSCF> BUILDER = new ResultSetConvertor<>() {
            @Override
            public ChinaMobileICSCF convert(ResultSet rs) throws SQLException {
                ChinaMobileICSCF icscf = new ChinaMobileICSCF();
                icscf.setCenterAreaCode(rs.getString("center_area_code"));
                icscf.setIp(rs.getString("ip"));
                icscf.setProvince("province");
                return icscf;
            }
        };
    }

    public static String getAreaCodeByICSCF(String icscfHost) {
        return cscfCenterAreaCode.get(icscfHost);
    }

    public static String getAreaCodeByProvinceAbbr(String provinceAbbr) {
        return provinceAbbrAreaCode.get(provinceAbbr);
    }

    public static String getProvinceByICSCF(String icscfHost) {
        return cscfIpProvince.get(icscfHost);
    }

}
