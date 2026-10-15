package com.cmic.dao.db;

import lombok.Getter;

public class Bill2DB extends BaseDB {
    @Getter
    private static final Bill2DB instance = new Bill2DB();

    public final static String DB_ALIAS_NAME = "bill2db";

    public Bill2DB() {
        super(DB_ALIAS_NAME);
    }
}
