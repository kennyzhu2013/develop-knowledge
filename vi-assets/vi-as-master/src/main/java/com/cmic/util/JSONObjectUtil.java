package com.cmic.util;

import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;

public class JSONObjectUtil {

    public static int getIntFromJsonWithDefault(JSONObject jsonObject, String key) {
        return getIntFromJsonWithDefault(jsonObject, key, 0);
    }

    public static int getIntFromJsonWithDefault(JSONObject jsonObject, String key, int defaultValue) {
        if (jsonObject.containsKey(key)) {
            String value = jsonObject.getString(key);
            if (!StringUtils.isEmpty(value) && StringUtils.isNumeric(value))
                return jsonObject.getInt(key);
            else
                return defaultValue;
        }
        return defaultValue;
    }

    public static String getStringFromJsonWithDefault(JSONObject jsonObject, String key) {
        return getStringFromJsonWithDefault(jsonObject, key, "");
    }

    public static String getStringFromJsonWithDefault(JSONObject jsonObject, String key, String defaultValue) {
        if (jsonObject.containsKey(key)) {
            String value = jsonObject.getString(key);
            if (value == null || "null".equalsIgnoreCase(value))
                return defaultValue;
            else
                return value.trim();
        }
        return defaultValue;
    }

    public static String getStringFromJson(JSONObject jsonObject, String key) {
        if (jsonObject.containsKey(key)) {
            return jsonObject.getString(key);
        }
        return null;
    }

    public static void main(String[] args) {
        JSONObject pp = new JSONObject();
        pp.put("abc", Integer.valueOf(9991));
        int value = getIntFromJsonWithDefault(pp, "abc", 0);
        System.out.println(value);
    }
}
