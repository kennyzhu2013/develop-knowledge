package com.cmic.pojo;

import com.common.dao.db.ResultSetConvertor;

import java.sql.ResultSet;
import java.sql.SQLException;

public class SpeTelbRule {
    int length;
    String speTelbHead;

    //0表示不需要+  +86
    //1表示需要+ +86
    int dispplus86;

    public SpeTelbRule(int length, String speTelbHead, int dispplus86) {
        this.length = length;
        this.speTelbHead = speTelbHead;
        this.dispplus86 = dispplus86;
    }

    public static final ResultSetConvertor<SpeTelbRule> BUILDER = new ResultSetConvertor<SpeTelbRule>() {
        public SpeTelbRule convert(ResultSet rs) throws SQLException {
            return new SpeTelbRule(rs.getInt("length"), rs.getString("speTelbHead"), rs.getInt("dispplus86"));
        }
    };

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public String getSpeTelbHead() {
        return speTelbHead;
    }

    public void setSpeTelbHead(String speTelbHead) {
        this.speTelbHead = speTelbHead;
    }

    public int getDispplus86() {
        return dispplus86;
    }

    public void setDispplus86(int dispplus86) {
        this.dispplus86 = dispplus86;
    }
}
