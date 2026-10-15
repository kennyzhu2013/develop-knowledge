package com.cmic.utils;


import com.cmic.dao.DccConfigDao;
import com.cmic.pojo.entity.FileDownHost;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;

@Component
public class FileDownHostManager {

    @Autowired
    private DccConfigDao dccConfigDao;

    public static HashMap<Integer, String> fileDownHostMap = new HashMap<>();

    public void init() {
        fileDownHostMap = new HashMap<Integer, String>();
        List<FileDownHost> list = dccConfigDao.selectAllInternFileDownHost();
        if (CollectionUtils.isNotEmpty(list)) {
            for (FileDownHost fileDownHost : list) {
                fileDownHostMap.put(fileDownHost.getLocation(), fileDownHost.getHost());
            }
        }
    }

    public void reload() {
        HashMap<Integer, String> newFileDownHostMap = new HashMap<Integer, String>();
        List<FileDownHost> list = dccConfigDao.selectAllInternFileDownHost();
        if (CollectionUtils.isNotEmpty(list)) {
            for (FileDownHost fileDownHost : list) {
                newFileDownHostMap.put(fileDownHost.getLocation(), fileDownHost.getHost());
            }
        }

        fileDownHostMap = newFileDownHostMap;
    }

}
