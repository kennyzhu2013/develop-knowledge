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
 * customer表信息
 *
 * @author XH
 * @version 1.0
 * @date 2026-03-15
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class CustomerDo {

    private Integer id;                 // int, 主键
    private String name;                // varchar(100), 企业英文名
    private String nickname;            // varchar(100), 企业中文简称
    private String fullname;            // varchar(100), 企业中文全称
    private Integer parentId;           // int, 父级企业ID
    private Integer status;             // tinyint(1), 状态: 1-启用, 2-停用, 3-注销
    private Integer ability;            // tinyint(1), 关联原子能力
    private String roleIdList;          // varchar(100), 关联角色id列表
    private String creator;             // varchar(50), 创建者
    private String comment;             // varchar(100), 备注
    private Timestamp createTime;       // datetime, 创建时间
    private Timestamp updateTime;       // datetime, 更新时间
    private Integer abilityStatus;      // tinyint, 能力状态: 1-可用, 0-不可用
    private String appId;               // varchar(100), 终端ID
    private String platformAppId;       // varchar(20), 能开平台对应的appId
    private Integer priority;           // int, 优先级
    private String url;                 // varchar(255), 质检复核回调地址
    private Integer isReuseAsr;         // tinyint, 是否复用历史ASR文本: 1-是, 0-否
    private Integer source;             // tinyint, 1-能开; 2-后台添加
    private Integer customerType;       // int, 1-正式, 2-试用, 3-联调
    private Integer totalMinutes;       // int, 质检分钟总数
    private Integer usedMinutes;        // int, 已用质检分钟
    private Integer validity;           // int, 有效期
    private Timestamp expireTime;       // datetime, 失效时间

    public static final ResultSetConvertor<CustomerDo> BUILDER = new ResultSetConvertor<CustomerDo>() {
        @Override
        public CustomerDo convert(ResultSet rs) throws SQLException {
            CustomerDo customer = new CustomerDo();
            customer.setId(rs.getInt("id"));
            customer.setName(rs.getString("name"));
            customer.setNickname(rs.getString("nickname"));
            customer.setFullname(rs.getString("fullname"));
            customer.setParentId(rs.getInt("parent_id"));
            customer.setStatus(rs.getInt("status"));
            customer.setAbility(rs.getInt("ability"));
            customer.setRoleIdList(rs.getString("role_id_list"));
            customer.setCreator(rs.getString("creator"));
            customer.setAbilityStatus(rs.getInt("ability_status"));
            customer.setAppId(rs.getString("app_id"));
            customer.setPlatformAppId(rs.getString("platform_app_id"));
            customer.setPriority(rs.getInt("priority"));
            customer.setUrl(rs.getString("url"));
            customer.setIsReuseAsr(rs.getInt("is_reuse_asr"));
            customer.setSource(rs.getInt("source"));
            customer.setCustomerType(rs.getInt("customer_type"));
            customer.setTotalMinutes(rs.getInt("total_minutes"));
            customer.setUsedMinutes(rs.getInt("used_minutes"));
            customer.setValidity(rs.getInt("validity"));
            customer.setExpireTime(rs.getTimestamp("expire_time"));
            return customer;
        }
    };
}
