package com.cmic.dao.db;

import lombok.Getter;

public class Bill1DB extends BaseDB {
    @Getter
    private static final Bill1DB instance = new Bill1DB();

    public final static String DB_ALIAS_NAME = "bill1db";

    public Bill1DB() {
        super(DB_ALIAS_NAME);
    }
}
