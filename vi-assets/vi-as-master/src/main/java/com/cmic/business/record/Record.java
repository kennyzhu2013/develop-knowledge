package com.cmic.business.record;


import com.cmic.business.businessparam.CallParameter;
import net.sf.json.JSONObject;

import java.util.HashMap;

public class Record {
    private static final String RECORD_SUFFIX = "recordSuffix";
    private static final String REVERSE_LEG = "reverseLeg";
    private static final String SEPARATE_LEG = "separateLeg";
    private static final String IS_RECORD = "isRecord";
    private static final String SEPARATE_EARLY = "separateEarly";

    private static final String BIND_ID = "bindId";


    public static Record instance = new Record();

    public static Record getInstance() {
        return instance;
    }


    public final JSONObject buildRecordParam(CallParameter callParameter) {

        RecordBuilder rb = new RecordBuilder();

        HashMap hm = callParameter.getSpecialRecordParams();
        rb.setExtension(hm);

        return rb.setIsRecord(callParameter.isNeedRecord())
                .setRecordSuffix(callParameter.getSuffix())
                .setSeparateLeg(callParameter.isSeparateLeg())
                .setReverseLeg(callParameter.isReverseLeg())
                .setSeparateEarly(callParameter.isSeparateEarly())
                .setBindId(callParameter.getBindId())
                .buildRecordParams();
    }


    static class RecordBuilder {
        //"recordSuffix""reverseLeg""separateLeg""isRecord"
        public RecordBuilder() {
        }

        public String recordSuffix;
        public boolean reverseLeg;
        public boolean separateLeg;
        public boolean isRecord = false;
        public boolean isSeparateEarly = false;

        public String bindId;

        public HashMap<String, String> extension;

        public JSONObject buildRecordParams() {
            JSONObject jb = new JSONObject();
            jb.put(RECORD_SUFFIX, recordSuffix);
            jb.put(REVERSE_LEG, reverseLeg);
            jb.put(SEPARATE_LEG, separateLeg);
            jb.put(IS_RECORD, isRecord);
            jb.put(SEPARATE_EARLY, isSeparateEarly);
            jb.put(BIND_ID, bindId);
            if (extension != null && extension.size() > 0) {
                jb.putAll(extension);
            }
            return jb;
        }


        public RecordBuilder setSeparateEarly(boolean separateEarly) {
            isSeparateEarly = separateEarly;
            return this;
        }

        public RecordBuilder setBindId(String bindId) {
            this.bindId = bindId;
            return this;
        }

        public RecordBuilder setIsRecord(boolean isRecord) {
            this.isRecord = isRecord;
            return this;
        }

        public RecordBuilder setRecordSuffix(String recordSuffix) {
            this.recordSuffix = recordSuffix;
            return this;
        }

        public RecordBuilder setReverseLeg(boolean reverseLeg) {
            this.reverseLeg = reverseLeg;
            return this;
        }

        public RecordBuilder setSeparateLeg(boolean separateLeg) {
            this.separateLeg = separateLeg;
            return this;
        }

        public RecordBuilder setExtension(HashMap hashMap) {
            extension = hashMap;
            return this;
        }

        public RecordBuilder setOtherParam(String[] strings) {
            if (extension == null) {
                extension = new HashMap<>();
            }
            extension.put(strings[0], strings[1]);
            return this;
        }
    }

}
