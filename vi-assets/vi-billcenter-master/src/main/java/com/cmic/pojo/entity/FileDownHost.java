package com.cmic.pojo.entity;

import com.common.dao.db.ResultSetConvertor;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileDownHost {

    private int id;

    private int location;

    private String host;

    private int hostType;

    private String comment;

    private Date createTime;

    private Date updateTime;

    public static final ResultSetConvertor<FileDownHost> FileDownHostMap = new ResultSetConvertor<FileDownHost>() {
        @Override
        public FileDownHost convert(ResultSet rs) throws SQLException {
            return new FileDownHost(
                    rs.getInt("id"),
                    rs.getInt("location"),
                    rs.getString("host"),
                    rs.getInt("host_type"),
                    rs.getString("comment"),
                    rs.getTimestamp("create_time"),
                    rs.getTimestamp("update_time")
            );
        }
    };

}
