package com.cmic.extension;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class ExtensionManager {

    private static Map<Integer, AbstractCtExtension> id_extensions = new HashMap<>();
    private static Logger logger = LoggerFactory.getLogger(ExtensionManager.class);
    private static ExtensionManager instance = new ExtensionManager();

    public static ExtensionManager getInstance() {
        return instance;
    }

    static {
        id_extensions.put(1, new StandardExtension(1, "x-mode"));
    }

    public static boolean isGZHExtId(int extId) {
        return extId == 7 || extId == 8 || extId == 16;
    }

    public AbstractCtExtension getExtension(int extensionId) {

        return id_extensions.get(extensionId);
    }
}
