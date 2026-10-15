package com.cmic.dao.db;

import lombok.Getter;

public class BaseProdNumDB extends BaseDB {
    @Getter
    private static final BaseProdNumDB instance = new BaseProdNumDB();

    public final static String DB_ALIAS_NAME = "baseProdNumDB";

    public BaseProdNumDB() {
        super(DB_ALIAS_NAME);
    }
}
