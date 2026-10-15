package com.cmic.sip.header.historyinfo;

public class HiParam {

    public static String CMIC_MARK = "cmicMark";

    private String name = "";
    private String value = "";

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "HiParam{" +
                "name='" + name + '\'' +
                ", value='" + value + '\'' +
                '}';
    }
}
