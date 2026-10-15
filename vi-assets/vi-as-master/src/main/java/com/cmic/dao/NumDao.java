package com.cmic.dao;

import com.cmic.dao.db.BaseProdNumDB;
import com.cmic.pojo.CheckNum;
import com.cmic.pojo.PreferMediaRule;
import com.cmic.pojo.SpeTelbRule;
import com.dcc.config.dao.DccConfigDb;
import com.dcc.config.model.BaseProdNum;
import lombok.Getter;


import java.util.ArrayList;

public class NumDao {

    @Getter
    private static final NumDao instance = new NumDao();

    public static ArrayList<SpeTelbRule> selectAllSpeRule() {
        return DccConfigDb.getInstance().executeQueryObjectList("select * from special_telb_rule", SpeTelbRule.BUILDER);
    }


    public static ArrayList<PreferMediaRule> selectAllPreferMediaRule() {
        return DccConfigDb.getInstance().executeQueryObjectList("select * from caller_prefer_media", PreferMediaRule.BUILDER);
    }

    public BaseProdNum selectBaseProdNumByNumber(long number)
    {
        // 保留右侧11位，再移除右侧8位
        long suffix = (number % 100000000000L) / 100000000L;
        String tableName = "base_prod_num_" + String.format("%03d", suffix);
        String querySql = "select * from " + tableName + " where number = ?";
        return BaseProdNumDB.getInstance().executeQueryObject(querySql, new Object[]{number}, BaseProdNum.BUILDER);
    }

    public static void main(String[] args) {
        long suffix = (700100100170000L % 100000000000L) / 100000000L;
        String tableName = "base_prod_num_" + String.format("%03d", suffix);
        System.out.println(tableName);
    }

    public ArrayList<CheckNum> selectAllCheckNums() {
        return DccConfigDb.getInstance().executeQueryObjectList("select * from check_section_no", CheckNum.BUILDER);
    }
}
