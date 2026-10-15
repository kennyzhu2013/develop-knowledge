package com.cmic.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AbnormalBillAnalyserConfig {

    public static final List<Boolean> allSwitch = Arrays.asList(true, true, true, true, false, true, true);

    public static boolean isOpenAnalyse() {
        return allSwitch.stream().anyMatch(Boolean::booleanValue);
    }
    
    public static void startAll() {
        Collections.fill(allSwitch, true);
    }

    public static void stopAll() {
        Collections.fill(allSwitch, false);
    }

    public static boolean case1Switch() {
        return allSwitch.get(0);
    }

    public static boolean case1Switch(boolean v) {
        return allSwitch.set(0, v);
    }

    public static boolean case2Switch() {
        return allSwitch.get(1);
    }

    public static boolean case2Switch(boolean v) {
        return allSwitch.set(1, v);
    }

    public static boolean case3Switch() {
        return allSwitch.get(2);
    }

    public static boolean case3Switch(boolean v) {
        return allSwitch.set(2, v);
    }

    public static boolean case4Switch() {
        return allSwitch.get(3);
    }

    public static boolean case4Switch(boolean v) {
        return allSwitch.set(3, v);
    }

    public static boolean case5Switch() {
        return allSwitch.get(4);
    }

    public static boolean case5Switch(boolean v) {
        return allSwitch.set(4, v);
    }

    public static boolean case6Switch() {
        return allSwitch.get(5);
    }

    public static boolean case6Switch(boolean v) {
        return allSwitch.set(5, v);
    }

    public static boolean case7Switch() {
        return allSwitch.get(6);
    }

    public static boolean case7Switch(boolean v) {
        return allSwitch.set(6, v);
    }
}
