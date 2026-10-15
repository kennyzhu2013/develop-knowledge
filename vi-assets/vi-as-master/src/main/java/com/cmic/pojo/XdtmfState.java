package com.cmic.pojo;

//eme代表左腿的early状态,mrf的early状态
//cmc代表左腿的confirmed状态，mrf的confirmed。
//cre代表左腿的confirmed状态，右腿的early。
//crc代表左腿的confirmed状态，右腿的confirmed。
//trt代表左腿的terminated状态，右腿的terminated。
public enum XdtmfState {
    eme(1, "eme"), cmc(2, "cmc"), cre(3, "cre"), crc(4, "crc"), trt(5, "trt");

    private XdtmfState(int state, String name) {
    }

    int state;
    String name;

    public void processRequest() {
    }

    public void processResponse() {
    }
}
