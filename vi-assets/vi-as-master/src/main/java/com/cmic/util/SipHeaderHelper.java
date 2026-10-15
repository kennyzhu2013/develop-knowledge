package com.cmic.util;

import gov.nist.javax.sip.message.SIPRequest;

import javax.sip.header.ContactHeader;
import javax.sip.header.RouteHeader;
import java.util.Iterator;
import java.util.ListIterator;

public class SipHeaderHelper {
    public static boolean ifSupported(SIPRequest request) {
        ListIterator list = request.getHeaders(ContactHeader.NAME);
        if (list == null) {
            return false;
        }
        while (list.hasNext()) {
            ContactHeader contactHeader = (ContactHeader) list.next();
            Iterator li = contactHeader.getParameterNames();
            while (li.hasNext()) {
                String parameter = (String) li.next();
                if (parameter.equalsIgnoreCase("video")) {
                    //同时还要支持amr-wb才能支持视频
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean ifRouteContainZte(RouteHeader routeHeader) {
        return routeHeader.getAddress().toString().contains("zte");
    }
}
