package com.cmic.utils;

import com.cmic.dao.MidAuthDao;
import com.cmic.pojo.entity.CompanyTab;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;

@Component
public class CompanyTabManager {

    @Autowired
    private MidAuthDao midAuthDao;

    public static HashMap<Integer, CompanyTab> companyTabMap = new HashMap<>();

    public void init(){
        companyTabMap =  new HashMap<Integer,CompanyTab>();
        List<CompanyTab> list = midAuthDao.selectAllCompanyTab();
        if (CollectionUtils.isNotEmpty(list)) {
            for (CompanyTab companyTab : list){
                companyTabMap.put(companyTab.getId(),companyTab);
            }
        }
    }

    public void reload(){
        HashMap<Integer,CompanyTab> newCompanyTabMap = new HashMap<Integer,CompanyTab>();
        List<CompanyTab> list = midAuthDao.selectAllCompanyTab();
        if (CollectionUtils.isNotEmpty(list)) {
            for (CompanyTab companyTab : list){
                newCompanyTabMap.put(companyTab.getId(),companyTab);
            }
        }

        companyTabMap = newCompanyTabMap;
    }

}
