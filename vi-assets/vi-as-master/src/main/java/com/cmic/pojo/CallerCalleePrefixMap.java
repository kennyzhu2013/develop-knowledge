package com.cmic.pojo;

import com.cmic.util.PhoneNumUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 主叫被叫映射关系
 * <p>
 * Author: luohuanfa
 * Date: 2023/12/28
 */
public class CallerCalleePrefixMap {
    private final List<String> callerPrefixList;
    private final List<String> calleePrefixList;

    public CallerCalleePrefixMap() {
        this.callerPrefixList = new ArrayList<>();
        this.calleePrefixList = new ArrayList<>();
    }

    public void addAllCaller(String[] caller) {
        callerPrefixList.addAll(List.of(caller));
    }

    public void addAllCallee(String[] callee) {
        calleePrefixList.addAll(List.of(callee));
    }

    public boolean isCallerContain(String caller) {
        return callerPrefixList.contains(caller);
    }

    public boolean isCallerStart(String caller) {
        String pureNumer = PhoneNumUtil.getPureNumer(caller);
        return callerPrefixList.stream().anyMatch(pureNumer::startsWith);
    }

    public boolean isCalleeContain(String callee) {
        return calleePrefixList.contains(callee);
    }

    public boolean isCalleeStart(String callee) {
        String pureNumer = PhoneNumUtil.getPureNumer(callee);
        return calleePrefixList.stream().anyMatch(pureNumer::startsWith);
    }

    @Override
    public String toString() {
        return "{" +
                "callerPrefixList=" + callerPrefixList +
                ", calleePrefixList=" + calleePrefixList +
                '}';
    }
}
