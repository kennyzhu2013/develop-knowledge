package com.cmic.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileTest {
    public static void main(String[] args) {
        String path = "sources/";
        File directory = new File(path);
        List<File> fileList = new ArrayList<>();
        CommonUtil.scanFiles(directory, "export",fileList);
        for (File file1 : fileList) {
            System.out.println(file1.getName());
        }

        String filename = "behavior.xml";
        File file = CommonUtil.findFileByName(fileList, filename);
        System.out.println(file.getName());


    }
}
