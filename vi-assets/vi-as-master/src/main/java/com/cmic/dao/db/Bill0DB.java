package com.cmic.dao.db;

import lombok.Getter;

public class Bill0DB extends BaseDB {
    @Getter
    private static final Bill0DB instance = new Bill0DB();

    public final static String DB_ALIAS_NAME = "bill0db";

    public Bill0DB() {
        super(DB_ALIAS_NAME);
    }
}
