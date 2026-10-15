package com.cmic.pojo;

import com.common.dao.db.ResultSetConvertor;

import java.sql.ResultSet;
import java.sql.SQLException;

public class PreferMediaRule {
    String caller;

    public String getCaller() {
        return caller;
    }

    public void setCaller(String caller) {
        this.caller = caller;
    }

    public String getPreferMedia() {
        return preferMedia;
    }

    public void setPreferMedia(String preferMedia) {
        this.preferMedia = preferMedia;
    }

    String preferMedia;

    public PreferMediaRule(String caller, String preferMedia) {
        this.caller = caller;
        this.preferMedia = preferMedia;
    }

    public static final ResultSetConvertor<PreferMediaRule> BUILDER = new ResultSetConvertor<PreferMediaRule>() {
        public PreferMediaRule convert(ResultSet rs) throws SQLException {
            return new PreferMediaRule(rs.getString("caller"), rs.getString("prefer_media"));
        }
    };
}
