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
 * channel_tab表信息
 *
 * @author Generated
 * @version 1.0
 * @date 2026-04-01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ChannelDo {

    private Integer id;
    private String name;
    private String nickname;
    private String fullname;
    private Integer parentId;
    private String roleIdList;
    private String creator;
    private String comment;
    private Timestamp createTime;
    private Timestamp updateTime;

    /**
     * 静态内部类：用于将 ResultSet 转换为 ChannelTabDo 对象
     */
    public static final ResultSetConvertor<ChannelDo> BUILDER = new ResultSetConvertor<ChannelDo>() {
        @Override
        public ChannelDo convert(ResultSet rs) throws SQLException {
            ChannelDo channel = new ChannelDo();
            channel.setId(rs.getInt("id"));
            channel.setName(rs.getString("name"));
            channel.setNickname(rs.getString("nickname"));
            channel.setFullname(rs.getString("fullname"));
            channel.setParentId(rs.getInt("parent_id"));
            channel.setRoleIdList(rs.getString("role_id_list"));
            channel.setCreator(rs.getString("creator"));
            return channel;
        }
    };
}
