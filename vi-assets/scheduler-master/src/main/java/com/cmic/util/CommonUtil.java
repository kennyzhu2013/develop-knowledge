package com.cmic.util;

import java.io.File;
import java.util.List;

public class CommonUtil {
    public static <T extends Comparable<T>> int compare(T a, T b) {
        return a.compareTo(b);
    }

    public static File findFileByName(List<File> files, String filename) {
        if (files == null || filename == null) {
            return null;
        }
        return files.stream()
                .filter(file -> file.getName().equals(filename))
                .findFirst()
                .orElse(null);
    }

    public static void scanFiles(File folder, String excludeDir, List<File> files) {
        if(folder.getName().contains(excludeDir)){
            return;
        }
        if (folder.isDirectory()) {
            File[] fileList = folder.listFiles();
            for (File file : fileList) {
                if (file.isDirectory()) {
                    scanFiles(file, excludeDir, files);
                } else {
                    files.add(file);
                }
            }
        }
    }
}
