package com.cmic.pojo;

import net.sf.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

public class SequenceParamsBill {

    String billId;

    int seqCnt;

    List<SequenceParams> sequenceParamsList = new ArrayList<>();

    public SequenceParamsBill(String billId) {
        this.billId = billId;
    }

    public String getBillId() {
        return billId;
    }

    public void setBillId(String billId) {
        this.billId = billId;
    }

    public int getSeqCnt() {
        return seqCnt;
    }

    public List<SequenceParams> getSequenceParamsList() {
        return sequenceParamsList;
    }

    public void setSeqCnt(int seqCnt) {
        this.seqCnt = seqCnt;
    }

    public void addSequenceParams(SequenceParams sequenceParams) {
        sequenceParamsList.add(sequenceParams);
    }


    public static class SequenceParams {
        int seq_id;
        String seq_num;
        String cf_time;
        String resp_time;
        String r_time;
        String seq_dp;
        int finishstate;
        int finishtype;

        public String getSeq_num() {
            return seq_num;
        }

        public void setSeq_num(String seq_num) {
            this.seq_num = seq_num;
        }

        public String getCf_time() {
            return cf_time;
        }

        public void setCf_time(String cf_time) {
            this.cf_time = cf_time;
        }

        public String getResp_time() {
            return resp_time;
        }

        public void setResp_time(String resp_time) {
            this.resp_time = resp_time;
        }

        public String getR_time() {
            return r_time;
        }

        public void setR_time(String r_time) {
            this.r_time = r_time;
        }

        public int getFinishstate() {
            return finishstate;
        }

        public void setFinishstate(int finishstate) {
            this.finishstate = finishstate;
        }

        public int getFinishtype() {
            return finishtype;
        }

        public void setFinishtype(int finishtype) {
            this.finishtype = finishtype;
        }

        int q850;

        public String getSeq_dp() {
            return seq_dp;
        }

        public void setSeq_dp(String seq_dp) {
            this.seq_dp = seq_dp;
        }


        public int getSeq_id() {
            return seq_id;
        }

        public void setSeq_id(int seqId) {
            this.seq_id = seqId;
        }


        public int getQ850() {
            return q850;
        }

        public void setQ850(int q850) {
            this.q850 = q850;
        }
    }

    public String toBillString() {
        return "{" +
                "\"seq_cnt\":" +
                seqCnt +
                "," +
                "\"seq_param\":" +
                JSONArray.fromObject(sequenceParamsList).toString() +
                "}";
    }
}
