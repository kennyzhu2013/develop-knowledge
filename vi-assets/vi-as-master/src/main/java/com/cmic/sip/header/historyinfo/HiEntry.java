package com.cmic.sip.header.historyinfo;

import java.util.ArrayList;
import java.util.List;

public class HiEntry {
    private String hiTargetedToUri;
    private List<HiParam> hiParamList = new ArrayList<HiParam>();
    private String hiExtension;

    public String getHiTargetedToUri() {
        return hiTargetedToUri;
    }

    public void setHiTargetedToUri(String hiTargetedToUri) {
        this.hiTargetedToUri = hiTargetedToUri;
    }

    public String getHiExtension() {
        return hiExtension;
    }

    public void setHiExtension(String hiExtension) {
        this.hiExtension = hiExtension;
    }

    public void addHiParam(HiParam hiParam){
        hiParamList.add(hiParam);
    }

    public List<HiParam> getHiParamList() {
        return hiParamList;
    }

    @Override
    public String toString() {
        return "HiEntry{" +
                "hiTargetedToUri='" + hiTargetedToUri + '\'' +
                ", hiParamList=" + hiParamList +
                ", hiExtension='" + hiExtension + '\'' +
                '}';
    }
}
