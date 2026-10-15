package com.cmic.utils;


import com.cmic.dao.MidAuthDao;
import com.cmic.pojo.entity.EnterpriseScene;
import com.cmic.pojo.entity.FileDownHost;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;

@Component
public class EnterpriseSceneManager {

    @Autowired
    private MidAuthDao midAuthDao;

    public static HashMap<Integer, String> enterpriseSceneMap = new HashMap<>();

    public void init() {
        enterpriseSceneMap = new HashMap<Integer, String>();
        List<EnterpriseScene> list = midAuthDao.selectAllEnterpriseScene();
        if (CollectionUtils.isNotEmpty(list)) {
            for (EnterpriseScene enterpriseScene : list) {
                enterpriseSceneMap.put(enterpriseScene.getId(), enterpriseScene.getWordPackageId());
            }
        }
    }

    public void reload() {
        HashMap<Integer, String> newEnterpriseSceneMap = new HashMap<Integer, String>();
        List<EnterpriseScene> list = midAuthDao.selectAllEnterpriseScene();
        if (CollectionUtils.isNotEmpty(list)) {
            for (EnterpriseScene enterpriseScene : list) {
                newEnterpriseSceneMap.put(enterpriseScene.getId(), enterpriseScene.getWordPackageId());
            }
        }

        enterpriseSceneMap = newEnterpriseSceneMap;
    }

}
