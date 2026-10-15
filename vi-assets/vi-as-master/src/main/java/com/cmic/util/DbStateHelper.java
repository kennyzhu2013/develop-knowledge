package com.cmic.util;

public class DbStateHelper {

    public static int genIsRecordOk(boolean isNeedRecord, int recordType) {
//        录音推送状态   0：待推  1：成功  -1：失败  10：无需推 20 :sftp拉取
        if (isNeedRecord && recordType == 2)//录音且用sftp拉取
        {
            return 20;
        }
        return isNeedRecord ? 0 : 10;
    }
}
