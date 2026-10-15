package com.cmic.util;

import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommonUtil {

    public static int convertPlayAudioRecord(String traceId, String playAudioRecord) {
        try {
            return Integer.parseInt(playAudioRecord, 2);
        } catch (Exception e) {
            DtmfLogger.error(traceId, " convertPlayAudioRecord exception ", e);
            return 0;
        }

    }

    public static boolean isNumericUsingRegex(String str) {
        return str.matches("\\d+");
    }

    public static String addPostfixToAudioFile(String audioCode,String audioFile){
        if(StringUtils.isBlank(audioFile)) {
            return "";
        }
        String[] audioFiles = audioFile.split(",");
        StringBuilder builder = new StringBuilder();
        for(String subAudioFile: audioFiles){
            String newAudioFile = "";
            String tempAudioFile = "";
            if(subAudioFile.indexOf(".") > 0){
                tempAudioFile = subAudioFile.substring(0,subAudioFile.indexOf("."));
            }else{
                tempAudioFile = subAudioFile;
            }
            switch (audioCode){
                case "amr-wb":
                    newAudioFile = tempAudioFile + "_wb.amr";
                    break;
                case "amr":
                    newAudioFile = tempAudioFile + "_nb.amr";
                    break;
                case "pcmu":
                    newAudioFile = tempAudioFile + "_pcmu.wav";
                    break;
                case "pcma":
                    newAudioFile = tempAudioFile + "_pcma.wav";
                    break;
                default:
                    newAudioFile = tempAudioFile + "_wb.amr";
                    break;
            }
            builder.append(newAudioFile).append(",");
        }
        builder.deleteCharAt(builder.length()-1);

        return builder.toString();
    }

    public static String addStrings(String a, String b) {
        // 预处理：将纯分号字符串视为空字符串
        a = normalizeString(a);
        b = normalizeString(b);


        // 处理空字符串
        if (StringUtils.isBlank(a)) return b;
        if (StringUtils.isBlank(b)) return a;

        a = a.trim();
        b = b.trim();

        // 分割字符串（保留空元素）
        ArrayList<String> aPartsList = new ArrayList<>(Arrays.asList(a.split(";", -1)));
        ArrayList<String> bPartsList = new ArrayList<>(Arrays.asList(b.split(";", -1)));

        // 长度不足的序列补足
        int maxLength = Math.max(aPartsList.size(), bPartsList.size());
        String appendStr = "";
        if(aPartsList.size() < maxLength) {
            if (!a.contains(";")) {
                appendStr = a;
            }
            while (aPartsList.size() < maxLength) aPartsList.add(appendStr);
        } else if (bPartsList.size() < maxLength) {
            if (!b.contains(";")) {
                appendStr = b;
            }
            while (bPartsList.size() < maxLength) bPartsList.add(appendStr);
        }

        // 合并对应部分
        List<String> mergedParts = new ArrayList<>();
        for (int i = 0; i < maxLength; i++) {
            String aPart = aPartsList.get(i);
            String bPart = bPartsList.get(i);

            StringBuilder merged = new StringBuilder();
            if (StringUtils.isNotBlank(aPart) && StringUtils.isNotBlank(bPart)) {
                merged.append(aPart).append(",").append(bPart);
            } else {
                merged.append(aPart).append(bPart);
            }
            mergedParts.add(merged.toString());
        }

        return String.join(";", mergedParts);
    }

    public static String normalizeString(String s) {
        if (StringUtils.isBlank(s)) return s;
        return s.chars().allMatch(c -> c == ';') ? "" : s;
    }

}
