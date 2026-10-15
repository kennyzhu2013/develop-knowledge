package com.cmic.util;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStreamReader;
import org.apache.commons.io.FileUtils;

public class SdpUtil {
    public static void getPrecondition(byte[] rawContent) {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(rawContent)));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.indexOf("a=des") > -1) {
                }
                if (line.indexOf("a=curr") > -1) {
                    String[] attributes = line.split(" ");
                    System.out.println(attributes[1]);
                    System.out.println(attributes[2]);
                }

            }
        } catch (Exception e) {

        }
    }

    public static byte[] getVideoSdp(byte[] input) throws Exception {
        InputStreamReader isr = new InputStreamReader(new ByteArrayInputStream(input));
        BufferedReader bf = new BufferedReader(isr);
        String line = bf.readLine();
        boolean isNeedAppend = false;
        StringBuffer sb = new StringBuffer();
        while (line != null) {
            if (line.contains("m=video")) {
                isNeedAppend = true;
            }
            if (isNeedAppend) {
                sb.append(line).append("\r\n");
            }
            line = bf.readLine();
        }
        return sb.toString().getBytes();
    }

    public static String getVideoSPS(byte[] input) throws Exception {
        InputStreamReader isr = new InputStreamReader(new ByteArrayInputStream(input));
        BufferedReader bf = new BufferedReader(isr);
        String line = bf.readLine();
        StringBuffer sb = new StringBuffer();
        while (line != null) {
            if (line.contains("sprop-parameter-sets")) {
                String[] sline = line.split(";");
                for (int i = 0; i < sline.length; i++) {
                    String s = sline[i];
                    if (s.contains("sprop-parameter-sets")) {
                        String[] a = s.split("sprop-parameter-sets=");
                        return a[1];
                    }
                }
            }
            line = bf.readLine();
        }
        return "";
    }
    public static byte[] processPrecondition(byte[] originSdp, PreconditionParam preconditionParam) {
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(originSdp)));
            String line;
            boolean isAudio = true;
            while ((line = br.readLine()) != null) {
                if (isAudio) {
                    if (line.contains("a=curr") || line.contains("a=des") || line.contains("a=conf")) {
                    } else if(line.contains("m=video") || line.contains("m=application")){
                        isAudio =false;
                        sb.append(preconditionParam.toString());
                    } else {
                        sb.append(line).append("\r\n");
                    }
                } else {

                }
            }
            // 如果前面没有遇见其他的m行，只有m=audio，则在最后再append
            if (isAudio) {
                sb.append(preconditionParam.toString());
            }
            return sb.toString().getBytes();
        } catch (Exception e) {
            return null;
        }
    }

    public static void main(String[] args) {
        try {
//            System.out.println(FileUtils.readFileToString(new File("invite.sdp")));
//            getPrecondition(FileUtils.readFileToByteArray(new File("invite.sdp")));
//
//            NameValue nv = new NameValue("curr", "qos curr sendrecv");
//            AttributeField newAF = new AttributeField();
//            newAF.setAttribute(nv);
//
//            System.out.println(newAF.toString());

            System.out.println(new String(processPrecondition(FileUtils.readFileToByteArray(new File("invite.sdp")), new PreconditionParam())));

        } catch (Exception e) {
        }
    }
}
