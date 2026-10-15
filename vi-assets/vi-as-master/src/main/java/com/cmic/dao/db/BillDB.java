package com.cmic.dao.db;

import lombok.Getter;

public class BillDB extends BaseDB {
    @Getter
    private static final BillDB instance = new BillDB();

    public final static String DB_ALIAS_NAME = "billdb";

    public BillDB() {
        super(DB_ALIAS_NAME);
    }
}
