package com.cmic.util;

import com.cmic.business.ViConf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author CMIC
 */
public class AuthorizationGenHelp {
    private static final Logger logger = LoggerFactory.getLogger(AuthorizationGenHelp.class);

    public static String getCommonAuthorizationHeader(ViConf viConf) {
        String serectKey = viConf.getCompany().getAppSecret();
        String platformId = viConf.getCompany().getAppKey();
        String authorizationHeader = AuthorizationUtil.getAuthorizationHeader(serectKey, platformId);
        logger.debug("authorizationHeader====" + authorizationHeader);
        return authorizationHeader;
    }

}
