package com.cmic.pojo;

import java.util.ArrayList;
import java.util.List;

public class SequenceCallsList {

    public static final String SEQUENCE_CALL_LIST_NAME = "sequenceCalls";
    public static final String SEQUENCE_CALLED_NO = "sequenceCalledNo";
    public static final String PLATFORM_INTEGRATION_SEQUENCE_CALLED_NO = "sequenceCalleeNo";
    public static final String SEQUENCE_CALL_NO_PLAY_CODE = "sequenceCallNoPlayCode";
    public static final String SEQUENCE_CALLED_PLAY_CODE = "sequenceCalledPlayCode";
    public static final String SEQUENCE_DISPLAY_NO = "sequenceDisplayNo";
    public static final String SEQUENCE_DATA = "sequenceData";
    //需要入库的顺振被叫字段，菜鸟阿里固话可能需要加0，所以需要独立一个字段
    public static final String SEQUENCE_BILL_SEQ_NO = "sequenceBillSeqNo";
    private int index;
    private List<SequenceCall> scl = new ArrayList<>();

    public SequenceCall getSequenceCalls() {
        if (index >= scl.size()) {
            return null;
        }
        return scl.get(index++);
    }

    public SequenceCall getSequenceCallByIndex(int index) {
        if (index >= scl.size()) {
            return null;
        }
        return scl.get(index);
    }

    public List<SequenceCall> getSequenceCallsList() {
        return scl;
    }

    public int getIndex() {
        return index;
    }

    public boolean isReachLast() {
        if (index >= scl.size()) {
            return true;
        }
        return false;
    }

    public SequenceCall getFirstSequenceCall() {
        if (scl.size() <= 0) {
            return null;
        }
        return scl.get(0);
    }

    public void addSequenceCall(SequenceCall sequenceCall) {
        scl.add(sequenceCall);
    }


    public SequenceCallsList() {

    }


    public static class SequenceCall {
        private String sequenceCalledNo;
        private String sequenceCalledNoPlayCode;
        private String sequenceCalledPlayCode;
        private String sequenceDisplayNo;
        private String sequenceData;
        private String sequenceBillCalledNo;

        public SequenceCall(String sequenceCalledNo, String sequenceCalledNoPlayCode, String sequenceCalledPlayCode, String sequenceDisplayNo, String sequenceBillCalledNo) {
            this.sequenceCalledNo = sequenceCalledNo;
            this.sequenceCalledNoPlayCode = sequenceCalledNoPlayCode;
            this.sequenceCalledPlayCode = sequenceCalledPlayCode;
            this.sequenceDisplayNo = sequenceDisplayNo;
            this.sequenceBillCalledNo = sequenceBillCalledNo;
        }

        public String getSequenceCalledNo() {
            return sequenceCalledNo;
        }

        public String getSequenceCalledNoPlayCode() {
            return sequenceCalledNoPlayCode;
        }

        public String getSequenceCalledPlayCode() {
            return sequenceCalledPlayCode;
        }

        public String getSequenceDisplayNo() {
            return sequenceDisplayNo;
        }

        public String getSequenceData() {
            return sequenceData;
        }

        public void setSequenceData(String sequenceData) {
            this.sequenceData = sequenceData;
        }

        public void setSequenceBillCalledNo(String sequenceBillCalledNo) {
            this.sequenceBillCalledNo = sequenceBillCalledNo;
        }

        public String getSequenceBillCalledNo() {
            return sequenceBillCalledNo;
        }

        public void setSequenceDisplayNo(String sequenceDisplayNo) {
            this.sequenceDisplayNo = sequenceDisplayNo;
        }

        @Override
        public String toString() {
            return "SequenceCall{" +
                    "sequenceCalledNo='" + sequenceCalledNo + '\'' +
                    ", sequenceCalledNoPlayCode='" + sequenceCalledNoPlayCode + '\'' +
                    ", sequenceCalledPlayCode='" + sequenceCalledPlayCode + '\'' +
                    ", sequenceDisplayNo='" + sequenceDisplayNo + '\'' +
                    ", sequenceData='" + sequenceData + '\'' +
                    ", sequenceBillCalledNo='" + sequenceBillCalledNo + '\'' +
                    '}';
        }
    }

}
