package com.cmic.utils;

import com.cmic.pojo.entity.CompanyTab;
import lombok.extern.slf4j.Slf4j;

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
