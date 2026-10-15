package com.cmic.dao;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.cmic.pojo.entity.CompanyTab;
import com.cmic.pojo.entity.CustomerTab;
import com.cmic.pojo.entity.EnterpriseScene;
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
            customerTab.setBillMode(rs.getInt("bill_mode"));
            customerTab.setScenarioId(rs.getString("scenario_id"));
            customerTab.setScriptId(rs.getInt("script_id"));

            return customerTab;
        }
    };


    public static final ResultSetConvertor<CompanyTab> companyTabMap = new ResultSetConvertor<CompanyTab>() {
        @Override
        public CompanyTab convert(ResultSet rs) throws SQLException {
            CompanyTab companyTab = new CompanyTab();

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

    public static final ResultSetConvertor<EnterpriseScene> enterpriseSceneMap = new ResultSetConvertor<EnterpriseScene>() {
        @Override
        public EnterpriseScene convert(ResultSet rs) throws SQLException {
            EnterpriseScene enterpriseScene = new EnterpriseScene();

            enterpriseScene.setId(rs.getInt("id"));
            enterpriseScene.setCreateTime(rs.getTimestamp("create_time"));
            enterpriseScene.setUpdateTime(rs.getTimestamp("update_time"));
            enterpriseScene.setCode(rs.getString("code"));
            enterpriseScene.setName(rs.getString("name"));
            enterpriseScene.setWordPackageId(rs.getString("word_package_id"));

            return enterpriseScene;
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

    public List<EnterpriseScene> selectAllEnterpriseScene() {
        String sql = "select * from enterprise_scene";

        // 安全查询处理空结果
        List<EnterpriseScene> result = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> enterpriseSceneMap.convert(rs)
        );

        return result.isEmpty() ? null : result;
    }


}
