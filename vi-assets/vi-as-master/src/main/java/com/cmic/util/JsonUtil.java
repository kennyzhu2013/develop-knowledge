package com.cmic.util;

import net.sf.json.JSONObject;

public class JsonUtil {
    public static String getStringFromJsonWithDefault(JSONObject jsonObject, String key) {
        if (jsonObject.containsKey(key)) {
            String value = jsonObject.getString(key);
            if (value == null || value.equalsIgnoreCase("null"))
                return "";
            else
                return value.trim();
        }
        return "";
    }
}
