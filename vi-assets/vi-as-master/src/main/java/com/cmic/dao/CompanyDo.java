package com.cmic.dao;

import com.common.dao.db.ResultSetConvertor;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * company表信息
 *
 * @author XH
 * @version 1.0
 * @date 2026-03-15
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class CompanyDo {

    private Integer id;
    private String name;
    private String nickname;
    private String fullname;
    private String appKey;
    private String appSecret;
    private Integer parentId;
    private Integer status;
    private String roleIdList;
    private String creator;
    private String comment;
    private Timestamp createTime;
    private Timestamp updateTime;

    /**
     * 静态内部类：用于将 ResultSet 转换为 CompanyDo 对象
     */
    public static final ResultSetConvertor<CompanyDo> BUILDER = new ResultSetConvertor<CompanyDo>() {
        @Override
        public CompanyDo convert(ResultSet rs) throws SQLException {
            CompanyDo company = new CompanyDo();
            company.setId(rs.getInt("id"));
            company.setName(rs.getString("name"));
            company.setNickname(rs.getString("nickname"));
            company.setFullname(rs.getString("fullname"));
            company.setAppKey(rs.getString("app_key"));
            company.setAppSecret(rs.getString("app_secret"));
            company.setParentId(rs.getInt("parent_id"));
            company.setStatus(rs.getInt("status"));
            company.setRoleIdList(rs.getString("role_id_list"));
            company.setCreator(rs.getString("creator"));
            return company;
        }
    };
}
