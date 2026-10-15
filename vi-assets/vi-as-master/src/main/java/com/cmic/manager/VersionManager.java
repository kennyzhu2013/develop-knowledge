package com.cmic.manager;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;


public class VersionManager {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    public static VersionManager instance = new VersionManager();

    public static VersionManager getInstance() {
        return instance;
    }

    private int version;

    public void readVersion() {
        try {
            File file = new File("version");
            if (file.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(file));
                this.version = Integer.parseInt(reader.readLine());
            } else {
                this.version = 0;
            }
        } catch (Exception e) {
            this.version = 0;
        }
    }

    public int getVersion()
    {
        return version;
    }
}
