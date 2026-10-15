package com.cmic.util;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class VideoLevelRecorder {
    private static Logger logger = LoggerFactory.getLogger(VideoLevelRecorder.class);
    private String leftVideoLevel;
    private String rightVideoLevel;
    private String leftVideoFrameRate;
    private String rightVideoFrameRate;

    private static Map<String, Format3gpVideoEnum> frameSizeLevel = new HashMap<>();
    // private static Map<String, Format3gpVideoEnum> frameSizeLevel30 = new HashMap<>();

    static {
        frameSizeLevel.put("ld", Format3gpVideoEnum.ld);
        frameSizeLevel.put("dl", Format3gpVideoEnum.dl);
        frameSizeLevel.put("ck", Format3gpVideoEnum.ck);
        frameSizeLevel.put("kc", Format3gpVideoEnum.kc);
        frameSizeLevel.put("jb", Format3gpVideoEnum.jb);
        frameSizeLevel.put("ie", Format3gpVideoEnum.ie);
        frameSizeLevel.put("ii", Format3gpVideoEnum.ii);
        frameSizeLevel.put("gg", Format3gpVideoEnum.gg);
        frameSizeLevel.put("fb", Format3gpVideoEnum.fb);
        frameSizeLevel.put("bf", Format3gpVideoEnum.bf);
        frameSizeLevel.put("fa", Format3gpVideoEnum.fa);

        frameSizeLevel.put("on", Format3gpVideoEnum.on);
        frameSizeLevel.put("no", Format3gpVideoEnum.no);
        frameSizeLevel.put("gm", Format3gpVideoEnum.gm);
        frameSizeLevel.put("lh", Format3gpVideoEnum.lh);
        frameSizeLevel.put("kk", Format3gpVideoEnum.kk);
    }

    public static boolean isResolutionValid(String resolution) {
        return frameSizeLevel.containsKey(resolution);
    }

    private static Format3gpVideoEnum getFormat3gpVideoEnum(String name) {
        if (frameSizeLevel.containsKey(name)) {
            return frameSizeLevel.get(name);
        }
        return Format3gpVideoEnum.other;
    }

    private String sessionId;

    public VideoLevelRecorder(String sessionId) {
        this.sessionId = sessionId;
    }

    public void recordVideoParams(boolean isLeft, byte[] rawContent) throws IOException {
        try {
            InputStreamReader bufferedReader = new InputStreamReader(new ByteArrayInputStream(rawContent));
            BufferedReader br = new BufferedReader(bufferedReader);
            String line = br.readLine();
            String videoPayload = "";
            String level = "";
            String frameRate = "";
            Integer maxbr = null;
            while (line != null) {
                if (line.startsWith("m=video")) {
                    String[] videoParams = line.trim().split(" ");
                    if (videoParams.length > 3) {
                        videoPayload = videoParams[3];
                    }
                }
                // a=fmtp:114 profile-level-id=42C01E;packetization-mode=1;sprop-parameter-sets=Z0LAHo2NQPAo01BQICB4RCKc,aMpDyA==
                if (line.startsWith("a=fmtp:" + videoPayload) && StringUtils.isNotBlank(videoPayload)) {
                    int levelIndex = line.indexOf("profile-level-id=");
                    if (levelIndex > -1 && line.length() > levelIndex + 22) {
                        level = line.substring(levelIndex + 21, levelIndex + 23);
                    }

                    int brIndex = line.indexOf("max-br=");
                    if (brIndex > -1) {
                        String brSubstring = line.substring(brIndex + 7);
                        int brEndIndex = brSubstring.contains(";") ? brSubstring.indexOf(";") : brSubstring.length();
                        String maxbrStr = brSubstring.substring(0, brEndIndex);
                        try {
                            maxbr = Integer.parseInt(maxbrStr);
                        } catch (Exception e) {
                            logger.error("[{}], VodPlugin-recordVideoParams, maxbr:{} parse error", sessionId, maxbrStr);
                        }
                    }
                }
                if (line.startsWith("a=framerate:")) {
                    frameRate = line.substring(12).trim();
                }
                line = br.readLine();
            }
            String thisRoundLevel = "1F".equals(level) ? "L31" : "L30";
            if (maxbr != null && maxbr < 1500) {
                thisRoundLevel = "L30";
            }
            setVideoLevel(isLeft, thisRoundLevel);
            String thisRoundFrameRate = "25".equals(frameRate) ? "25" : "30";
            setVideoFrameRate(isLeft, thisRoundFrameRate);
        } catch (Exception e) {
            logger.error("[{}], [vodplugin recordVideoParams], record error", sessionId, e);
        }
    }

    /**
     * 设置level
     *
     * @param level
     */
    void setVideoLevel(boolean isLeft, String level) {
        if (isLeft) {
            leftVideoLevel = level;
        } else {
            rightVideoLevel = level;
        }
    }

    /**
     * 设置framerate
     *
     * @param frameRate
     */
    public void setVideoFrameRate(boolean isLeft, String frameRate) {
        if (isLeft) {
            leftVideoFrameRate = frameRate;
        } else {
            rightVideoFrameRate = frameRate;
        }
    }

    public String getLeftVideoLevel() {
        return leftVideoLevel;
    }

    public String getRightVideoLevel() {
        return rightVideoLevel;
    }

    public String getBothLowerVideoLevel() {
        if ("L30".equals(rightVideoLevel) || "L30".equals(leftVideoLevel)) {
            return "L30";
        }
        return "L31";
    }

    public String getLeftVideoFrameRate() {
        return leftVideoFrameRate;
    }

    public String getRightVideoFrameRate() {
        return rightVideoFrameRate;
    }


    /**
     * select 3pg file
     *
     * @param fileMap 可供选择的文件map
     * @param isLeft  是否左腿
     * @return map 含有两个键值filePath，fileLevel
     */
    public Video3gpFile selectProper3gpFile(Map<String, String> fileMap, boolean isLeft) {
        Format3gpVideoEnum format3gpVideoEnum = null;
        Video3gpFile final3gpFile = new Video3gpFile();
        String targetLevel = isLeft ? leftVideoLevel : rightVideoLevel;
        for (Map.Entry<String, String> en : fileMap.entrySet()) {
            String formatName = en.getKey();
            String filePath = en.getValue();
            Format3gpVideoEnum tempFormat = getFormat3gpVideoEnum(formatName);
            if (tempFormat == null) {
                logger.error("[{}], [VideoLevelRecorder-selectProper3gpFile], with a invalid format:{}", sessionId, formatName);
                tempFormat = Format3gpVideoEnum.other;
            }
            if (format3gpVideoEnum == null) {
                format3gpVideoEnum = tempFormat;
                final3gpFile.setFilePath(filePath);
                final3gpFile.setFileLevel(tempFormat.getType());
                final3gpFile.setFormatName(tempFormat.name());
                continue;
            }
            // 如果当前format不是目标的level，现在来的却是目标level，则设置为当前格式
            if (!format3gpVideoEnum.getType().equals(targetLevel) && tempFormat.getType().equals(targetLevel)) {
                format3gpVideoEnum = tempFormat;
                final3gpFile.setFilePath(filePath);
                final3gpFile.setFileLevel(tempFormat.getType());
                final3gpFile.setFormatName(tempFormat.name());
                continue;
            }
            // 同level选取最高优先级
            if (format3gpVideoEnum.getType().equals(tempFormat.getType()) && format3gpVideoEnum.getPriorityLevel() < tempFormat.getPriorityLevel()) {
                format3gpVideoEnum = tempFormat;
                final3gpFile.setFilePath(filePath);
                final3gpFile.setFileLevel(tempFormat.getType());
                final3gpFile.setFormatName(tempFormat.name());
            }
        }
        return final3gpFile;
    }


    public static class Video3gpFile{
        private String formatName;
        private String filePath;
        private String fileLevel;

        public String getFormatName() {
            return formatName;
        }

        public void setFormatName(String formatName) {
            this.formatName = formatName;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public String getFileLevel() {
            return fileLevel;
        }

        public void setFileLevel(String fileLevel) {
            this.fileLevel = fileLevel;
        }
    }

}