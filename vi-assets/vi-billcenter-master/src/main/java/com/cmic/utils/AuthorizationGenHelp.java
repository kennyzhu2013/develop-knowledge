package com.cmic.utils;

import com.cmic.pojo.entity.CompanyTab;
import com.cmic.pojo.entity.CustomerTab;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
public class AuthorizationGenHelp {


    public static String getCommonAuthorizationHeader(CompanyTab companyTab) {
        String appId = companyTab.getAppKey();
        String appKey = companyTab.getAppSecret();
        String authorizationHeader = AuthorizationUtil.getAuthorizationHeader(appId, appKey);
        log.debug("authorizationHeader====" + authorizationHeader);
        return authorizationHeader;
    }


}
