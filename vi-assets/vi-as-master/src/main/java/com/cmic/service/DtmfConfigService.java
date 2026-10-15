package com.cmic.service;

import com.cmic.dao.NumDao;
import com.cmic.pojo.PreferMediaRule;

import java.util.*;

public class DtmfConfigService {
    HashMap<String, String> preferMediaCallerList = new HashMap<>();

    private static DtmfConfigService instance = new DtmfConfigService();

    public static DtmfConfigService getInstance() {
        return instance;
    }

    public void init() {
        ArrayList<PreferMediaRule> pmrList = NumDao.selectAllPreferMediaRule();
        Optional.ofNullable(pmrList).ifPresent(preferMediaRules -> preferMediaRules.forEach(t -> preferMediaCallerList.put(t.getCaller(), t.getPreferMedia())));
    }

    public void reload() {
        ArrayList<PreferMediaRule> pmrList = NumDao.selectAllPreferMediaRule();
        HashMap<String, String> newPreferMediaCallerList = new HashMap<>();
        Optional.ofNullable(pmrList).ifPresent(preferMediaRules -> preferMediaRules.forEach(t -> newPreferMediaCallerList.put(t.getCaller(), t.getPreferMedia())));
        preferMediaCallerList = newPreferMediaCallerList;
    }

    public String ifContainCallerAndGetPreferMedia(String caller) {
        if (preferMediaCallerList.containsKey(caller)) {
            return preferMediaCallerList.get(caller);
        } else {
            Iterator<Map.Entry<String, String>> it = preferMediaCallerList.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, String> e = it.next();
                if (caller.startsWith(e.getKey())) {
                    return e.getValue();
                }
            }
        }
        return "";
    }

}
