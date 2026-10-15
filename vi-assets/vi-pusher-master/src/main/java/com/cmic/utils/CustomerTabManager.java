package com.cmic.utils;

import com.cmic.dao.MidAuthDao;
import com.cmic.pojo.entity.CustomerTab;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;

@Component
public class CustomerTabManager {

    @Autowired
    private MidAuthDao midAuthDao;

    public static HashMap<Integer, CustomerTab> customerTabMap = new HashMap<>();

    public void init() {
        customerTabMap = new HashMap<Integer, CustomerTab>();
        List<CustomerTab> list = midAuthDao.selectAllCustomerTab();
        if (CollectionUtils.isNotEmpty(list)) {
            for (CustomerTab customerTab : list) {
                customerTabMap.put(customerTab.getId(), customerTab);
            }
        }
    }

    public void reload() {
        HashMap<Integer, CustomerTab> newCustomerTabMap = new HashMap<Integer, CustomerTab>();
        List<CustomerTab> list = midAuthDao.selectAllCustomerTab();
        if (CollectionUtils.isNotEmpty(list)) {
            for (CustomerTab customerTab : list) {
                newCustomerTabMap.put(customerTab.getId(), customerTab);
            }
        }

        customerTabMap = newCustomerTabMap;
    }

}
