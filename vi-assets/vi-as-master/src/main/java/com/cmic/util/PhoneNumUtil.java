package com.cmic.util;

import com.cmic.business.businessparam.CallParameter;
import com.cmic.pojo.AuditResponseEntity;
import com.cmic.pojo.SequenceCallsList;
import com.cmic.service.PhoneSegmentCacheService;
import com.dcc.config.cache.CityCache;
import com.dcc.config.util.PhoneUtil;
import org.apache.commons.lang.StringUtils;

import javax.sip.address.SipURI;
import javax.sip.address.TelURL;
import javax.sip.address.URI;
import java.util.HashMap;
import java.util.regex.Pattern;


/**
 * @author XXXX
 */
public class PhoneNumUtil {

    public static final String CHINA_COUNTRY_CALLING_CODE = "86";
    public static final HashMap<String, String> phonePrefixMap = new HashMap<String, String>();
    public static final String CHINA_MOBILE = "CM";
    public static final String CHINA_UNION = "CN";
    public static final String CHINA_TELECOM = "CT";
    public static final String CHINA_BROADCAST = "CB";
    public static final String UNKNOWN = "UNKNOWN";

    static {
        String[] chinaMobilePrefixList = new String[]{"134", "135", "136", "137", "138", "139", "1440", "147", "148", "150", "151", "152", "157", "158","159", "165", "172", "178", "182", "183", "184", "187", "188", "195", "197","198", "1703", "1705", "1706"};
        String[] chinaUnicomPrefixList = new String[]{"130", "131", "132", "1400", "145", "146", "155", "156", "166", "167", "171", "175", "176", "185", "186", "196","1704", "1707", "1708","1709"};
        // 174目前先默认是电信
        String[] chinaTelecomPrefixList = new String[]{"133", "1349", "1410", "149", "153", "162", "173", "174", "177", "180", "181", "189", "190", "191", "193", "199", "1700", "1701", "1702"};
        String[] chinaBroadcastPrefixList = new String[]{"192"};
        for (String s : chinaMobilePrefixList) {
            phonePrefixMap.put(s, CHINA_MOBILE);
        }
        for (String s : chinaUnicomPrefixList) {
            phonePrefixMap.put(s, CHINA_UNION);
        }
        for (String s : chinaTelecomPrefixList) {
            phonePrefixMap.put(s, CHINA_TELECOM);
        }
        for (String s : chinaBroadcastPrefixList) {
            phonePrefixMap.put(s, CHINA_BROADCAST);
        }
    }

    public static String getPhoneTeleOperator(String phoneNum) {
        phoneNum = getPure11NumberWithout0or86(phoneNum);
        if(StringUtils.isBlank(phoneNum) || phoneNum.length() < 3) return UNKNOWN;
        String start3char = phoneNum.substring(0, 3);
        String teleOperator = phonePrefixMap.get(start3char);
        if(StringUtils.isNotBlank(teleOperator)){
            return teleOperator;
        } else {
            if(phoneNum.length() < 4) {
                return UNKNOWN;
            } else {
                String start4char = phoneNum.substring(0, 4);
                teleOperator = phonePrefixMap.get(start4char);
                return StringUtils.isNotBlank(teleOperator) ? teleOperator : UNKNOWN;
            }
        }
    }

    public static String getPureNumer(String number) {
        if (number == null) {
            return "";
        }
        return number.indexOf("86") == 0 ? number.substring(2) : number;
    }

    public static String getPure11NumberWithout0or86(String number) {
        if (number == null) {
            return "";
        }
        if (number.startsWith("sip:") || number.startsWith("tel:")) {
            number = number.substring(4);
        }
        number = number.replaceAll("[^0-9]", "");
        number = number.trim();
        while (number.startsWith("0")) {
            number = number.substring(1);
        }
        if (number.startsWith("86")) {
            number = number.substring(2);
        }
        while (number.startsWith("0")) {
            number = number.substring(1);
        }
        return number;
    }

    public static void main(String[] args) {
        String calledNo = "+8612345678901";
        System.out.println(getPure11NumberWithout0or86("+8612345678901"));
        System.out.println(calledNo);
    }

    public static String getPure11NumberWithout0or86(URI uri) {
        String number = null;
        if (uri.isSipURI()) {
            number = ((SipURI) uri).getUser().trim();
        } else {
            number = ((TelURL) uri).getPhoneNumber().trim();
        }

        int posofcolon = number.indexOf(";");
        if (posofcolon > -1) {
            number = number.substring(0, posofcolon);
        }

        String pureNumber = number.replaceAll("[^0-9]", "");
        return getPure11NumberWithout0or86(pureNumber);
    }

    public static String trimCallTarget(String target) {
        if (target.startsWith("sip:") || target.startsWith("tel:")) {
            target = target.substring(4);
        }
        if (target.startsWith("+")) {
            target = target.substring(1);
        }
        if (target.startsWith("86")) {
            target = target.substring(2);
        }
        return target;
    }

    public static String adjustPhoneNo(String phoneNo, String billId) {
        if (StringUtils.isEmpty(phoneNo)) {
            return phoneNo;
        }
        String trimPhoneNo = phoneNo.trim();
        // 中国的号码
        if (trimPhoneNo.startsWith("+86") || trimPhoneNo.startsWith("0086") || trimPhoneNo.startsWith("+0086") || trimPhoneNo.startsWith("86")) {
            return PhoneUtil.processNumberCleaning(trimPhoneNo);
        } else {
            if (trimPhoneNo.startsWith("+")) {
                DtmfLogger.info(billId + "", "adjustedPhoneNo = {} is an international phone change + to 00", trimPhoneNo);
                trimPhoneNo = trimPhoneNo.replaceFirst("\\+", "00");
            }
            return trimPhoneNo;
        }

    }

    public static String clearNumberPrefix(String phoneNo) {
        if (StringUtils.isEmpty(phoneNo)) {
            return "";
        }
        String trimPhoneNo = phoneNo.trim();
        return PhoneUtil.processNumberCleaning(trimPhoneNo);
    }

    public static String adjustPhoneNumber(String phoneNo) {
        if (StringUtils.isEmpty(phoneNo)) {
            return "";
        }
        String trimPhoneNo = phoneNo.trim();
        if(StringUtils.isNotBlank(trimPhoneNo)){
            trimPhoneNo = trimPhoneNo.replaceAll("[^0-9]", "");
        }
        return trimPhoneNo;
    }

    public static boolean isValidMobilePhone(String phone, String tag) {
        String phoneWithout86 = getPhoneWithout86(phone);
        if (org.apache.commons.lang3.StringUtils.isBlank(phoneWithout86)) {
            return false;
        } else if (!org.apache.commons.lang3.StringUtils.isNumeric(phoneWithout86)) {
            return false;
        } else {
            return phoneWithout86.length() != 11 ? false : Pattern.matches("^1[3456789]\\d{9}", phoneWithout86);
        }
    }

    public static String getPhoneWithout86(String phoneNo) {
        String rtnPhoneNo = phoneNo;
        if (phoneNo == null) {
            return "";
        } else {
            if (phoneNo.startsWith("86")) {
                rtnPhoneNo = phoneNo.substring(2);
            }

            while(rtnPhoneNo.startsWith("0")) {
                rtnPhoneNo = rtnPhoneNo.substring(1);
            }

            return rtnPhoneNo;
        }
    }

    public static boolean isValidFixPhone(String phone, String tag) {
        String phoneWithout86 = getPhoneWithout86(phone);
        if (StringUtils.isBlank(phoneWithout86)) {
            return false;
        } else if (!StringUtils.isNumeric(phoneWithout86)) {
            return false;
        } else if (phoneWithout86.length() >= 9 && phoneWithout86.length() <= 11) {
            String areaCode = phoneWithout86.substring(0, 2);
            if (PhoneSegmentCacheService.isAreaCodeValid(areaCode)) {
                return true;
            }
            areaCode = phoneWithout86.substring(0, 3);
            if (PhoneSegmentCacheService.isAreaCodeValid(areaCode)) {
                return true;
            }
            return false;
        } else {
            return false;
        }
    }

    public static String getFixPhoneAreaCode(String phone) {
        String phoneWithout86 = getPhoneWithout86(phone);
        if (StringUtils.isBlank(phoneWithout86)) {
            return "";
        } else if (!StringUtils.isNumeric(phoneWithout86)) {
            return "";
        } else if (phoneWithout86.length() >= 9 && phoneWithout86.length() <= 11) {
            String areaCode = phoneWithout86.substring(0, 2);
            if (PhoneSegmentCacheService.isAreaCodeValid(areaCode)) {
                return areaCode;
            }
            areaCode = phoneWithout86.substring(0, 3);
            if (PhoneSegmentCacheService.isAreaCodeValid(areaCode)) {
                return areaCode;
            }
            return "";
        } else {
            return "";
        }
    }




}
