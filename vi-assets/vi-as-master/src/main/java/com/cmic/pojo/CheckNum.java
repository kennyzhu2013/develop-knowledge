package com.cmic.pojo;

import com.common.dao.db.ResultSetConvertor;
import lombok.Data;

import java.sql.ResultSet;
import java.sql.SQLException;

@Data
public class CheckNum {
    private int id;
    private String num;
    //被拨测公司ID
    private int user_id;
    //振诚拨测的公司ID
    private int check_user_id;

    private String check_application_id;


    public static final ResultSetConvertor<CheckNum> BUILDER = new ResultSetConvertor<CheckNum>() {
        @Override
        public CheckNum convert(ResultSet rs) throws SQLException {
            return new CheckNum(rs.getInt("id"), rs.getString("section_no"),
                    rs.getInt("user_id"), rs.getInt("check_user_id"), rs.getString("check_application_id"));
        }
    };


    public CheckNum(int id, String num, int user_id, int check_user_id, String check_application_id) {
        this.id = id;
        this.num = num;
        this.user_id = user_id;
        this.check_user_id = check_user_id;
        this.check_application_id = check_application_id;
    }
}
