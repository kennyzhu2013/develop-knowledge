package com.cmic;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;

import java.net.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Enumeration;

public class MainTest {
    public static void main(String[] args) {
        String mac = null;
        try {
            mac = getMacAddress().replace(":","");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        System.out.println(mac);


        try {
            String hostName = InetAddress.getLocalHost().getHostName();
            System.out.println(hostName);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }

        String ip = getNonLoopbackIpAddress();
        System.out.println(ip);
    }

    public static String getNonLoopbackIpAddress() {
        try {
            // 获取所有的网络接口
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface ni = networkInterfaces.nextElement();

                // 排除回环接口 (loopback) 和虚拟接口
                if (ni.isUp() && !ni.isLoopback() && !ni.isVirtual()) {

                    // 获取该接口的所有 IP 地址
                    Enumeration<InetAddress> inetAddresses = ni.getInetAddresses();
                    while (inetAddresses.hasMoreElements()) {
                        InetAddress inetAddress = inetAddresses.nextElement();

                        // 过滤掉 IPv6 地址，只获取 IPv4 地址
                        if (inetAddress instanceof java.net.Inet4Address) {
                            return inetAddress.getHostAddress();  // 返回第一个找到的非回环 IPv4 地址
                        }
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return null;  // 如果没有找到非回环地址，返回 null
    }

    public static String getExternalIpAddress() {
        try {
            // 获取所有网络接口
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                // 排除环回地址和虚拟网络接口
                if (networkInterface.isUp() && !networkInterface.isLoopback()) {
                    // 获取所有IP地址
                    Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                    while (inetAddresses.hasMoreElements()) {
                        InetAddress inetAddress = inetAddresses.nextElement();
                        if (inetAddress instanceof Inet4Address) {  // 过滤IPv4地址
                            return inetAddress.getHostAddress();  // 返回IP地址
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;  // 如果无法获取，返回null
    }

    public static String getMacAddress() throws Exception {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            if (networkInterface.isUp()) {
                byte[] mac = networkInterface.getHardwareAddress();
                if (mac != null) {
                    StringBuilder macAddr = new StringBuilder();
                    for (byte b : mac) {
                        macAddr.append(String.format("%02X:", b));
                    }
                    return macAddr.toString().substring(0, macAddr.length() - 1);
                }
            }
        }
        return null;  // 如果没有找到有效的 MAC 地址
    }

    public static boolean isValidPriority(String priority) {
        // 修改后的正则表达式，仅匹配单个数字
        String regex = "^[0-9]$";
        return priority.matches(regex);
    }

    private static void snow(){
        Snowflake snowflake = IdUtil.createSnowflake(1, 1);
        long id =  snowflake.nextId();
        System.out.println(id);

        // 解析雪花算法生成的 ID，获取时间戳
        long timestamp = snowflake.getGenerateDateTime(id);

        System.out.println("Generated ID: " + id);
        System.out.println("Timestamp extracted from ID: " + timestamp);

        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.ofHours(8));

        // 格式化日期时间
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String formattedDateTime = dateTime.format(formatter);

        // 输出格式化后的日期时间
        System.out.println("格式化后的日期时间: " + formattedDateTime);
    }

}
