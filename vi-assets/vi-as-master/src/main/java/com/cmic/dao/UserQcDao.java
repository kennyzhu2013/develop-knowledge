package com.cmic.dao;

import com.cmic.dao.db.UserQcDB;
import com.cmic.service.ChinaMobileIcscfCacheService;
import lombok.Getter;

import java.util.ArrayList;

/**
 * 主叫号码登记信息记录访问
 *
 * @author XH
 * @version 1.0
 * @date 2026-03-13
 */
public class UserQcDao {

    @Getter
    private static final UserQcDao instance = new UserQcDao();

    /**
     * 查询 biz_num_auth 表中所有记录
     * @return 包含所有 BizNumAuthDo 对象的列表
     */
    public ArrayList<BizNumAuthDo> selectAllCallerNumAuth() {
        String querySql = "SELECT * FROM biz_num_auth";
        // 查询所有主叫号码登记信息
        return UserQcDB.getInstance().executeQueryObjectList(
                querySql,
                BizNumAuthDo.BUILDER
        );
    }

    /**
     * 查询 company_tab 表中所有记录
     * @return 包含所有 CompanyDo 对象的列表
     */
    public ArrayList<ChannelDo> selectAllChannel() {
        String querySql = "SELECT * FROM channel_tab";
        // 查询所有主叫号码登记信息
        return UserQcDB.getInstance().executeQueryObjectList(
                querySql,
                ChannelDo.BUILDER
        );
    }

    /**
     * 查询 company_tab 表中所有记录
     * @return 包含所有 CompanyDo 对象的列表
     */
    public ArrayList<CompanyDo> selectAllCompany() {
        String querySql = "SELECT * FROM company_tab";
        // 查询所有主叫号码登记信息
        return UserQcDB.getInstance().executeQueryObjectList(
                querySql,
                CompanyDo.BUILDER
        );
    }

    /**
     * 查询 customer_tab 表中所有记录
     * @return 包含所有 CustomerDo 对象的列表
     */
    public ArrayList<CustomerDo> selectAllCustomer() {
        String querySql = "SELECT * FROM customer_tab";
        // 查询所有主叫号码登记信息
        return UserQcDB.getInstance().executeQueryObjectList(
                querySql,
                CustomerDo.BUILDER
        );
    }

    /**
     * 查询 chinamobile_icscf_ip 表中所有记录
     * @return 包含所有 ChinaMobileICSCF 对象的列表
     */
    public ArrayList<ChinaMobileIcscfCacheService.ChinaMobileICSCF> selectAllChinaMobileICSCF() {
        String querySql = "select ip,center_area_code,province from chinamobile_icscf_ip";
        // 查询所有主叫号码登记信息
        return UserQcDB.getInstance().executeQueryObjectList(
                querySql,
                ChinaMobileIcscfCacheService.ChinaMobileICSCF.BUILDER
        );
    }





}
