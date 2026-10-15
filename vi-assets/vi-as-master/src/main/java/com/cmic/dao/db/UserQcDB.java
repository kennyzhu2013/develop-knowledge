package com.cmic.dao.db;

import lombok.Getter;

/**
 * 主叫号码登记信息
 *
 * @author XH
 * @version 1.0
 * @date 2026-03-13
 */
public class UserQcDB extends BaseDB{

    @Getter
    private static final UserQcDB instance = new UserQcDB();

    public static final String DB_ALIAS_NAME = "userQc";

    public UserQcDB() {
        super(DB_ALIAS_NAME);
    }
}
