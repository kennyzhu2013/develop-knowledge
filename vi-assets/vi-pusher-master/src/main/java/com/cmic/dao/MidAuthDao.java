package com.cmic.dao;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.cmic.pojo.entity.CompanyTab;
import com.cmic.pojo.entity.CustomerTab;
import com.common.dao.db.ResultSetConvertor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
@DS("user_qc")
public class MidAuthDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;


    public static final ResultSetConvertor<CustomerTab> customerTabMap = new ResultSetConvertor<CustomerTab>() {
        @Override
        public CustomerTab convert(ResultSet rs) throws SQLException {
            CustomerTab customerTab = new CustomerTab();
/**
 * id` int NOT NULL AUTO_INCREMENT COMMENT '主键id',
 *   `name` varchar(100) NOT NULL COMMENT '企业英文名（可用拼音代替）',
 *   `nickname` varchar(100) NOT NULL DEFAULT '' COMMENT '企业中文简称',
 *   `fullname` varchar(100) NOT NULL COMMENT '企业中文全称',
 *   `parent_id` int NOT NULL COMMENT '父级企业ID',
 *   `status` tinyint(1) NOT NULL DEFAULT '3' COMMENT '状态，枚举值：1-启用、2-停用、3-注销',
 *   `ability` tinyint(1) NOT NULL COMMENT '关联原子能力，离线asr-1，实时asr-2，质检-3',
 *   `role_id_list` varchar(100) DEFAULT '' COMMENT '关联角色id列表，以逗号分隔',
 *   `creator` varchar(50) NOT NULL DEFAULT '' COMMENT '创建者',
 *   `comment` varchar(100) DEFAULT '' COMMENT '备注',
 *   `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 *   `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
 *   `ability_status` tinyint NOT NULL DEFAULT '1' COMMENT '能力状态，1-可用，0-不可用',
 *   `app_id` varchar(100) NOT NULL COMMENT '终端ID',
 *   `platform_app_id` varchar(20) NOT NULL DEFAULT 'retention' COMMENT '能开平台对应的appId',
 *   `priority` int NOT NULL COMMENT '优先级',
 *   `url` varchar(255) NOT NULL DEFAULT '' COMMENT '质检复核回调地址',
 *   `is_reuse_asr` tinyint NOT NULL DEFAULT '0' COMMENT '是否复用历史ASR文本 1-是 0-否',
 *   `source` tinyint NOT NULL DEFAULT '2' COMMENT '1-能开;2-后台添加',
 *   `customer_type` int NOT NULL DEFAULT '2' COMMENT '1-正式(formal);2-试用(trial);3-联调(test)',
 *   `total_minutes` int NOT NULL DEFAULT '0' COMMENT '质检分钟总数，充值后更新，范围1-100000',
 *   `used_minutes` int DEFAULT '0' COMMENT '已用质检分钟，定时任务扫描后更新',
 *   `validity` int DEFAULT NULL COMMENT '有效期,范围：0-180',
 *   `expire_time` datetime DEFAULT NULL COMMENT '失效时间',
 */
            customerTab.setId(rs.getInt("id"));
            customerTab.setName(rs.getString("name"));
            customerTab.setNickname(rs.getString("nickname"));
            customerTab.setFullname(rs.getString("fullname"));
            customerTab.setParentId(rs.getInt("parent_id"));
            customerTab.setStatus(rs.getInt("status"));
            customerTab.setAbility(rs.getInt("ability"));
            customerTab.setRoleIdList(rs.getString("role_id_list"));
            customerTab.setCreator(rs.getString("creator"));
            customerTab.setComment(rs.getString("comment"));
            customerTab.setCreateTime(rs.getTimestamp("create_time"));
            customerTab.setUpdateTime(rs.getTimestamp("update_time"));
            customerTab.setAbilityStatus(rs.getInt("ability_status"));
            customerTab.setAppId(rs.getString("app_id"));
            customerTab.setPlatformAppId(rs.getString("platform_app_id"));
            customerTab.setPriority(rs.getInt("priority"));
            customerTab.setUrl(rs.getString("url"));
            customerTab.setIsReuseAsr(rs.getInt("is_reuse_asr"));
            customerTab.setSource(rs.getInt("source"));
            customerTab.setCustomerType(rs.getInt("customer_type"));
            customerTab.setTotalMinutes(rs.getInt("total_minutes"));
            customerTab.setUsedMinutes(rs.getInt("used_minutes"));
            customerTab.setValidity(rs.getInt("validity"));
            customerTab.setExpireTime(rs.getTimestamp("expire_time"));
            customerTab.setAccountType(rs.getString("account_type"));
            customerTab.setResultPushUrl(rs.getString("result_push_url"));
            return customerTab;
        }
    };


    public static final ResultSetConvertor<CompanyTab> companyTabMap = new ResultSetConvertor<CompanyTab>() {
        @Override
        public CompanyTab convert(ResultSet rs) throws SQLException {
            CompanyTab companyTab = new CompanyTab();
            /**
             * `id` int NOT NULL AUTO_INCREMENT COMMENT '主键id',
             *   `name` varchar(100) NOT NULL COMMENT '企业英文名（可用拼音代替）',
             *   `nickname` varchar(100) NOT NULL DEFAULT '' COMMENT '企业中文简称',
             *   `fullname` varchar(100) NOT NULL COMMENT '企业中文全称',
             *   `app_key` varchar(50) NOT NULL COMMENT '企业鉴权id',
             *   `app_secret` varchar(100) NOT NULL COMMENT '企业鉴权秘钥',
             *   `parent_id` int NOT NULL COMMENT '父级企业ID',
             *   `status` tinyint(1) NOT NULL DEFAULT '3' COMMENT '状态，枚举值：1-启用、2-停用、3-注销',
             *   `role_id_list` varchar(100) DEFAULT '' COMMENT '关联角色id列表，以逗号分隔',
             *   `creator` varchar(50) NOT NULL DEFAULT '' COMMENT '创建者',
             *   `comment` varchar(100) DEFAULT '' COMMENT '备注',
             *   `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
             *   `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
             */
            companyTab.setId(rs.getInt("id"));
            companyTab.setName(rs.getString("name"));
            companyTab.setNickname(rs.getString("nickname"));
            companyTab.setFullname(rs.getString("fullname"));
            companyTab.setAppKey(rs.getString("app_key"));
            companyTab.setAppSecret(rs.getString("app_secret"));
            companyTab.setParentId(rs.getInt("parent_id"));
            companyTab.setStatus(rs.getInt("status"));
            companyTab.setRoleIdList(rs.getString("role_id_list"));
            companyTab.setCreator(rs.getString("creator"));
            companyTab.setComment(rs.getString("comment"));
            companyTab.setCreateTime(rs.getTimestamp("create_time"));
            companyTab.setUpdateTime(rs.getTimestamp("update_time"));

            return companyTab;
        }
    };


    public List<CompanyTab> selectAllCompanyTab() {
        String sql = "select * from company_tab";

        // 安全查询处理空结果
        List<CompanyTab> result = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> companyTabMap.convert(rs)
        );

        return result.isEmpty() ? null : result;

    }


    public List<CustomerTab> selectAllCustomerTab() {
        String sql = "select * from customer_tab";

        // 安全查询处理空结果
        List<CustomerTab> result = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> customerTabMap.convert(rs)
        );

        return result.isEmpty() ? null : result;

    }


}
