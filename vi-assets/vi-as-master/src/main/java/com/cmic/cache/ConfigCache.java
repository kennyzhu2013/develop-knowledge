package com.cmic.cache;

import org.apache.commons.lang.StringUtils;

import java.sql.SQLOutput;
import java.util.HashSet;
import java.util.Set;

public class ConfigCache {

    private static Set<String> zjjlIpListSet= new HashSet<String>();

    public static Set<String> getZjjlIpListSet() {
        return zjjlIpListSet;
    }

    public static void refreshZheJiangJiLingIp(String ipString){
        if(StringUtils.isNotBlank(ipString)){
            Set<String> newZheJiangJiLingIPSet = new HashSet<>();
            String[] items = ipString.split(",");
            for(String item : items){
                if(StringUtils.isNotBlank(item)){
                    newZheJiangJiLingIPSet.add(item.trim());
                }
            }
            zjjlIpListSet = newZheJiangJiLingIPSet;
        }
    }

    public static void main(String[] args) {
        ConfigCache.refreshZheJiangJiLingIp("10.192.117.34,10.192.117.35,10.192.136.92,10.192.136.93,10.192.118.149,10.192.118.150,10.192.119.180,10.192.119.181,10.192.116.2,10.192.116.3,10.192.116.10,10.192.116.11,10.192.120.114,10.192.120.115,10.188.28.25,10.188.28.27,10.188.28.24,10.188.28.23,10.188.28.26,10.188.28.22,10.188.28.21,10.188.28.20,10.188.28.29,10.188.28.30,10.188.28.31，10.188.28.32，10.188.28.19，10.188.28.18");
        System.out.println(zjjlIpListSet);



    }
}
