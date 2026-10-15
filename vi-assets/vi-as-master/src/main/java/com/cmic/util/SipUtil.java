package com.cmic.util;

import com.cmic.config.AppConfig;
import com.cmic.service.ChinaMobileIcscfCacheService;
import com.cmic.service.MyRegistryService;
import com.cmic.service.PhoneSegmentCacheService;
import com.cmic.service.XmodePromExporterService;
import com.cmic.sip.B2buaHelper;
import com.cmic.sip.engine.SipEngine;
import com.cmic.sip.header.historyinfo.HiEntry;
import com.cmic.sip.header.historyinfo.HiParam;
import com.cmic.sip.header.historyinfo.HistoryInfoHeader;
import com.dcc.config.util.PhoneUtil;
import com.util.IPPort;
import gov.nist.core.Separators;
import gov.nist.javax.sip.address.AddressImpl;
import gov.nist.javax.sip.address.TelURLImpl;
import gov.nist.javax.sip.header.*;
import gov.nist.javax.sip.header.ims.PAssertedIdentity;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import gov.nist.javax.sip.parser.StringMsgParser;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.address.TelURL;
import javax.sip.address.URI;
import javax.sip.header.*;
import javax.sip.message.Message;
import javax.sip.message.Request;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Pattern;

import static com.cmic.config.ConstantConf.XSIPPROXY_TAG;

/**
 * Created by wukai on 2020/01/29
 */
public class SipUtil {
    public static final Logger logger = LoggerFactory.getLogger(SipUtil.class);

    public static final String HISTORY_INFO_NAME = "History-Info";
    public static final String HISTORY_INFO_FORMAT = "<%s;Reason=SIP%%3Bcause%%3D%d>;index=1,<%s>;index=1.1";

    private static boolean isTagInRequire(SIPMessage message, String tag) {
        ListIterator<SIPHeader> list = message.getHeaders(RequireHeader.NAME);
        if (list == null) {
            return false;
        }
        while (list.hasNext()) {
            RequireHeader requireHeader = (RequireHeader) list.next();
            if (requireHeader.getOptionTag().equalsIgnoreCase(tag)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTagInSupported(SIPMessage request, String tag) {
        ListIterator<SIPHeader> list = request.getHeaders(SupportedHeader.NAME);
        if (list == null) {
            return false;
        }
        while (list.hasNext()) {
            SupportedHeader supportedHeader = (SupportedHeader) list.next();
            if (supportedHeader.getOptionTag().equalsIgnoreCase(tag)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isRequirePrecondition(SIPMessage response) {
        return isTagInRequire(response, "precondition");
    }

    public static boolean ifSupportVideo(SIPMessage sipMessage) throws Exception {
        ListIterator list = sipMessage.getHeaders(ContactHeader.NAME);
        if (list == null) {
            return false;
        }
        while (list.hasNext()) {
            ContactHeader contactHeader = (ContactHeader) list.next();
            Iterator li = contactHeader.getParameterNames();
            while (li.hasNext()) {
                String parameter = (String) li.next();
                if (parameter.equalsIgnoreCase("video")) {
                    // 同时还要支持amr-wb才能支持视频
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isSupportedPrecondition(SIPMessage request) {
        return isTagInSupported(request, "precondition");
    }


    public static boolean isRequire100rel(SIPResponse response) {
        RSeq header = (RSeq) response.getHeader(RSeqHeader.NAME);
        return response.getStatusCode() / 100 == 1 && header != null && isTagInRequire(response, "100rel");
    }


    public static boolean isSupported100rel(SIPRequest request) {
        return isTagInSupported(request, "100rel");
    }

    public static boolean isRequireTimer(SIPResponse response) {
        return isTagInRequire(response, "timer");
    }

    public static boolean isSupportedTimer(SIPRequest request) {
        return isTagInSupported(request, "timer");
    }

    /**
     * get clean number without '+' '00' '86' '0086' '0' '860'
     *
     * @param number phoneNumber
     * @return clean number without '+' '00' '86' '0086' '0' '860'
     */
    public static String GetPhoneNumber(String number) {
        int posofcolon = number.indexOf(";");
        if (posofcolon > -1) {
            number = number.substring(0, posofcolon);
        }
        String pureNumber = PhoneNumUtil.getPure11NumberWithout0or86(number);
        // 还有固定电话去掉0也不带86, 默认7001去掉区号
        String phoneNumberWithoutAreaCode = removeAreaCode(pureNumber);
        if (StringUtils.isNotBlank(phoneNumberWithoutAreaCode) && phoneNumberWithoutAreaCode.startsWith("7001")) {
            return phoneNumberWithoutAreaCode;
        } else {
            return pureNumber;
        }
    }

    /**
     * 处理 0208766434 208766434 这两种前面携带区号的情况，去掉前面的区号（不处理86）
     *
     * @param pureNumber
     * @return
     */
    public static String removeAreaCode(String pureNumber) {
        String phoneWithout0 = pureNumber.indexOf("0") == 0 ? pureNumber.substring(1) : pureNumber;
        if (phoneWithout0.length() > 4) {
            String first2 = phoneWithout0.substring(0, 2);
            if (PhoneSegmentCacheService.isAreaCodeValid(first2)) {
                return phoneWithout0.substring(2);
            }
            String first3 = phoneWithout0.substring(0, 3);
            if (PhoneSegmentCacheService.isAreaCodeValid(first3)) {
                return phoneWithout0.substring(3);
            }
        }
        return phoneWithout0;
    }

    /**
     * 处理 0208766434 208766434 这两种前面携带区号的情况，去掉前面的区号（不处理86）
     *
     * @param number
     * @return
     */
    public static String getAreaCode(String number) {
        String pureNumber = number;
        int posofcolon = pureNumber.indexOf(";");
        if (posofcolon > -1) {
            pureNumber = pureNumber.substring(0, posofcolon);
        }
        pureNumber = PhoneNumUtil.getPure11NumberWithout0or86(pureNumber);
        if (pureNumber.length() > 4) {
            String first2 = pureNumber.substring(0, 2);
            if (PhoneSegmentCacheService.isAreaCodeValid(first2)) {
                return first2;
            }
            String first3 = pureNumber.substring(0, 3);
            if (PhoneSegmentCacheService.isAreaCodeValid(first3)) {
                return first3;
            }
        }
        return "";
    }


    /**
     * Get clean number from Address
     *
     * @param address Address
     * @return clean number
     */
    public static String GetPhoneNumber(Address address) {
        // P-Asserted-Identity: <sip:+8615225809017@ha.ims.mnc000.mcc460.3gppnetwork.org>
        if (address == null) {
            return "";
        }
        String uri = address.getURI().toString();
        int index = uri.indexOf("@");
        if (index != -1) {
            uri = uri.substring(0, index);
        }
        return GetPhoneNumber(uri);
    }

    public static String GetPhoneNumber(URI uri) {
        String number = null;
        if (uri.isSipURI()) {
            number = ((SipURI) uri).getUser().trim();
        } else {
            number = ((TelURL) uri).getPhoneNumber().trim();
        }
        return GetPhoneNumber(number);
    }

    public static String getRawPhoneNum(URI uri) {
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
        return pureNumber;
    }

    /**
     * 从指定URI中提取号码，去除空格和末尾分号
     *
     * @param uri 目标URI
     * @return URI中的号码
     */
    public static String getActualRawPhoneNum(URI uri) {
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
        return number;
    }

    /**
     * 从指定URI中提取号码，去除空格和末尾分号,保留+号
     *
     * @param uri 目标URI
     * @return URI中的号码
     */
    public static String getOriginalPhoneNum(URI uri) {
        String number = "";
        if (uri.isSipURI()) {
            number = ((SipURI) uri).getUser().trim();
        } else {
            number = uri.toString().split(":")[1].trim();
        }
        int posofcolon = number.indexOf(";");
        if (posofcolon > -1) {
            number = number.substring(0, posofcolon);
        }
        return number;
    }

    public static String getOriginalCallerNumber(SIPRequest request) {
        return getOriginalCallerNumber(request, false);
    }

    /**
     * 从request请求中的From头域或者PAI头域提取号码，保留除空格和末尾分号之外的所有字符
     *
     * @param request   原始请求
     * @param preferPAI 是否优先使用PAI
     * @return 原始的号码
     */
    public static String getOriginalCallerNumber(SIPRequest request, boolean preferPAI) {
        String fromNumber = getOriginalPhoneNum(request.getFrom().getAddress().getURI());
        PAssertedIdentity pai = (PAssertedIdentity) request.getHeader(PAssertedIdentity.NAME);
        String paiNumber = "";
        if (pai != null) {
            paiNumber = getOriginalPhoneNum(pai.getAddress().getURI());
        }
        // 如果需要优先使用PAI，则返回PAI，否则返回From
        if (preferPAI) {
            return paiNumber.isEmpty() ? fromNumber : paiNumber;
        }
        return fromNumber.isEmpty() ? paiNumber : fromNumber;
    }

    public static String getCallerNum(SIPRequest request) {
        String fromNumber = GetPhoneNumber(request.getFrom().getAddress().getURI());
        PAssertedIdentity pai = (PAssertedIdentity) request.getHeader(PAssertedIdentity.NAME);
        String paiNumber = "";
        if (pai != null) {
            paiNumber = GetPhoneNumber(pai.getAddress().getURI());
        }
        return fromNumber.isEmpty() ? paiNumber : fromNumber;
    }

    public static String getCalleeNum(SIPRequest request) {
        return GetPhoneNumber(request.getRequestURI());
    }

    public static String getRawCallerNum(SIPRequest request) {
        return getRawCallerNum(request, false);
    }

    public static String getRawCallerNum(SIPRequest request, boolean preferPai) {
        String fromNumber = getRawPhoneNum(request.getFrom().getAddress().getURI());
        PAssertedIdentity pai = (PAssertedIdentity) request.getHeader(PAssertedIdentity.NAME);
        String paiNumber = "";
        if (pai != null) {
            paiNumber = getRawPhoneNum(pai.getAddress().getURI());
        }
        // 如果需要优先使用PAI，则返回PAI，否则返回From
        if (preferPai) {
            return paiNumber.isEmpty() ? fromNumber : paiNumber;
        }
        return fromNumber.isEmpty() ? paiNumber : fromNumber;
    }

    public static String getActualRawCallerNum(SIPRequest request) {
        String fromNumber = getRawPhoneNum(request.getFrom().getAddress().getURI());
        PAssertedIdentity pai = (PAssertedIdentity) request.getHeader(PAssertedIdentity.NAME);
        String paiNumber = "";
        if (pai != null) {
            paiNumber = getActualRawPhoneNum(pai.getAddress().getURI());
        }
        return fromNumber.isEmpty() ? paiNumber : fromNumber;
    }


    public static void changeDisplayNum(SipEngine engine, SIPRequest request, String callerNum, String calledDisplayNo, String abbreviation) throws Exception {
        changeDisplayNum(engine, request, callerNum, calledDisplayNo, abbreviation, "");

    }

    public static void changeDisplayNum(SipEngine engine, SIPRequest request, String callerNum, String calledDisplayNo, String abbreviation, String paiNum) throws Exception {
//        P-Asserted-Identity: "+8613802885219" <sip:+8613802885219@gd.ims.mnc000.mcc460.3gppnetwork.org>,"+8613802885219" <tel:+8613802885219>
//        P-Asserted-Identity: "+8613802885277" <sip:+8613802885277@gd.ims.mnc000.mcc460.3gppnetwork.org>
        if (paiNum == null || paiNum.isEmpty()) {
            paiNum = calledDisplayNo;
        }
        if (!StringUtils.isEmpty(calledDisplayNo) && calledDisplayNo.startsWith("12539")) {
            //修改From
            request.setHeader(engine.headerFactory.createFromHeader(engine.addressFactory.createAddress(engine.addressFactory.createTelURL(calledDisplayNo)), request.getFrom().getTag()));
            //修改PAI
            ListIterator pAssertedIdentities = request.getHeaders("P-Asserted-Identity");
            request.removeHeader("P-Asserted-Identity");
            boolean hasAddTel = false;
            while (pAssertedIdentities.hasNext()) {
                PAssertedIdentity header = (PAssertedIdentity) pAssertedIdentities.next();
                PAssertedIdentity pai = new PAssertedIdentity();
                URI uri = header.getAddress().getURI();
                if (uri.isSipURI()) {
                    String newDisplayNo = calledDisplayNo;
                    if (StringUtils.isNotBlank(newDisplayNo) && newDisplayNo.startsWith("86")) {
                        newDisplayNo = "+" + newDisplayNo;
                    } else {
                        newDisplayNo = "+86" + calledDisplayNo;
                    }
                    SipURI sipURI = engine.addressFactory.createSipURI(newDisplayNo, engine.getHost());
                    Iterator iterator = ((SipURI) uri).getParameterNames();
                    boolean hasAddUserPhone = false;
                    while (iterator.hasNext()) {
                        String name = (String) iterator.next();
                        sipURI.setParameter(name, ((SipURI) uri).getParameter(name));
                        if (name.equalsIgnoreCase("user")) {
                            hasAddUserPhone = true;
                        }
                    }
                    if (!hasAddUserPhone) {
                        sipURI.setParameter("user", "phone");
                    }
                    Address address = engine.addressFactory.createAddress(sipURI);
                    address.setDisplayName(newDisplayNo);
                    pai.setAddress(address);
                } else {
                    TelURL telURL = engine.addressFactory.createTelURL(calledDisplayNo);
                    Iterator iterator = ((TelURL) uri).getParameterNames();
                    boolean hasAddPhoneContext = false;
                    while (iterator.hasNext()) {
                        String name = (String) iterator.next();
                        telURL.setParameter(name, ((TelURL) uri).getParameter(name));
                        if (name.equalsIgnoreCase("phone-context")) {
                            hasAddPhoneContext = true;
                        }
                    }
                    if (!hasAddPhoneContext) {
                        telURL.setParameter("phone-context", "+86");
                    }
                    Address address = engine.addressFactory.createAddress(telURL);
                    address.setDisplayName(calledDisplayNo);
                    pai.setAddress(address);
                }
                request.addHeader(pai);
                hasAddTel = true;
            }
            if (!hasAddTel) {
                TelURL telURL = engine.addressFactory.createTelURL(calledDisplayNo);
                telURL.setParameter("phone-context", "+86");
                Address address = engine.addressFactory.createAddress(telURL);
                address.setDisplayName(calledDisplayNo);
                PAssertedIdentity pai = new PAssertedIdentity();
                pai.setAddress(address);
                request.addHeader(pai);
            }
        } else {
            ArrayList<String> assertedList = new ArrayList<>();
            if (request.getHeader("P-Asserted-Identity") != null) {
                // 如果需要替换PAI中的号码，需要获取原始的主叫号码（优先从PAI获取，包含所有非符号前缀）
                String replaceCaller = getOriginalCallerNumber(request, true);
                paiNum = "+86" + GetPhoneNumber(paiNum);
                ListIterator<SIPHeader> assertHeaders = request.getHeaders("P-Asserted-Identity");
                while (assertHeaders.hasNext()) {
                    SIPHeader assertHeader = assertHeaders.next();
                    String assertedValue = assertHeader.toString().split("Identity:")[1].trim();
                    if (abbreviation != null && !abbreviation.isEmpty()) {
                        // 替换不同省份域名缩写
                        if (assertedValue.contains("mcc460.3gppnetwork.org")) {
                            assertedValue = assertedValue.replaceFirst("[a-z][a-z].ims.mnc[0-9]*.mcc460.3gppnetwork.org", abbreviation + ".ims.mnc000.mcc460.3gppnetwork.org");
                        }
                    }
                    assertedList.add(assertedValue.replace(replaceCaller, paiNum));
                }
            }
            if (request.getHeader("P-Asserted-Identity") != null && !assertedList.isEmpty()) {
                request.removeHeader("P-Asserted-Identity");
                for (String assertedValue : assertedList) {
                    request.addHeader(engine.headerFactory.createHeader("P-Asserted-Identity", assertedValue));

                }
            }

            URI fromURI = request.getFrom().getAddress().getURI();
            String rawFromNumber = getOriginalPhoneNum(fromURI);
            String newCalledDisplayNo = calledDisplayNo;
            //String displayNoWithoutPlus = calledDisplayNo;
            if (Pattern.compile("^1([3-9])\\d{9}$").matcher(calledDisplayNo).matches()) {
                newCalledDisplayNo = "+86" + calledDisplayNo;
                //displayNoWithoutPlus = "86"+displayNoWithoutPlus;
            } else if (calledDisplayNo.startsWith("86")) {
                newCalledDisplayNo = "+" + calledDisplayNo;
            }

            String fromHeader = request.getHeader(FromHeader.NAME).toString().split("From:")[1].trim();
            //String newFromHeader = fromHeader.replace(callerNum, calledDisplayNo);
            String newFromHeader = fromHeader.replace(rawFromNumber, newCalledDisplayNo);
            if (!newFromHeader.contains(calledDisplayNo)) {
                request.setHeader(engine.headerFactory.createFromHeader(engine.addressFactory.createAddress(engine.addressFactory.createTelURL(newCalledDisplayNo)), request.getFrom().getTag()));
            } else {
                FromHeader sipFromHeader = (FromHeader) engine.headerFactory.createHeader("From", newFromHeader);
                Address address = sipFromHeader.getAddress();
                if (address != null) {
                    String origDisplayName = sipFromHeader.getAddress().getDisplayName();
                    if (StringUtils.isNotBlank(origDisplayName)) {
                        sipFromHeader.getAddress().setDisplayName(newCalledDisplayNo);
                    }
                }
                request.setHeader(sipFromHeader);
                //request.setHeader(engine.headerFactory.createHeader("From", newFromHeader));
            }
        }

    }

    public static void dccDisplayNoAdjust(SIPRequest request, String calledDisplayNo) throws Exception {
        PAssertedIdentity pAssertedIdentity = new PAssertedIdentity();
        AddressImpl addressImpl = new AddressImpl();
        StringMsgParser smp = new StringMsgParser();
        String phoneWithout86 = PhoneUtil.getPhoneWithout86(calledDisplayNo);
        TelURLImpl tel = (TelURLImpl) smp.parseUrl("tel:" + phoneWithout86);
        addressImpl.setURI(tel);
        pAssertedIdentity.setAddress(addressImpl);
        request.getFrom().setAddress(addressImpl);
        request.setHeader(pAssertedIdentity);
    }

    public static void addProxyRoute(SIPRequest request, IPPort proxy, SipEngine engine) {
        try {
            boolean isAddSipProxyRoute = request.getRouteHeaders().getFirst().toString().contains("x-sip-proxy");
            // 没有添加过带"x-sip-proxy"的Route才会添加
            if (!isAddSipProxyRoute) {
                SipURI sipUri = engine.addressFactory.createSipURI("x-sip-proxy", proxy.ip);
                sipUri.setPort(proxy.port);
                sipUri.setLrParam();
                sipUri.setTransportParam("udp");
                final Address internLocalAddress = engine.addressFactory.createAddress("x-sip-proxy", sipUri);
                RouteHeader proxyRouteHeader = engine.headerFactory.createRouteHeader(internLocalAddress);
                request.getRouteHeaders().addFirst((Route) proxyRouteHeader);
            }
        } catch (Exception e) {
            logger.error("addProxyRoute error send INVITE without proxy Route", e);
        }
    }


    public static String getUserAgent(SIPMessage message) throws Exception {
        SIPHeader header = (SIPHeader) message.getHeader("User-Agent");
        if (header == null) {
            return "";
        }
        return header.getHeaderValue();
    }

    public static String getPearlyMediaValue(SIPMessage message) throws Exception {
        SIPHeader header = (SIPHeader) message.getHeader("P-Early-Media");
        if (header == null) {
            return "";
        }
        return header.getHeaderValue();
    }

    String ss = "P-Asserted-Identity: \"+8617896201803\" <sip:+8617896201803@gd.ims.mnc000.mcc460.3gppnetwork.org>,\"+8617896201803\" <tel:+8617896201803> ";
    String st = "P-Asserted-Identity: \"+8613802885219\" <sip:+8613802885219@gd.ims.mnc000.mcc460.3gppnetwork.org>,\"+8613802885219\" <tel:+8613802885219> ";
    String s1 = "P-Asserted-Identity: \"+8613802885277\" <sip:+8613802885277@gd.ims.mnc000.mcc460.3gppnetwork.org>,\"+8613802885277\" <tel:+8613802885277> ";

    public static String getSpecialRouteValueByRegex(SIPRequest request, List<String> regexPatterns) {
        ListIterator list = request.getHeaders(RouteHeader.NAME);
        if (list == null) {
            return "";
        }
        while (list.hasNext()) {
            RouteHeader routeHeader = (RouteHeader) list.next();
            String routeValue = routeHeader.getAddress().toString();
            for (String regex : regexPatterns) {
                if (Pattern.matches(regex, routeValue)) {
                    return routeValue;
                }
            }
        }
        return "";

    }

    public static boolean ifSupportVideo(Message request) {
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
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean hasQos(Message request) throws Exception {
        byte[] rawContent = request.getRawContent();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(rawContent)))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("a=curr:") || line.startsWith("a=des:") || line.startsWith("a=conf")) {
                    return true;
                }
            }
        }
        return false;
    }

    public static HistoryInfoHeader parseHistoryInfo(Header historyInfo) throws Exception {
        String historyInfoStr = historyInfo.toString();
        HistoryInfoHeader historyInfoHeader = new HistoryInfoHeader();
        int startIndex = "History-Info:".length();
        String historyInfoValue = historyInfoStr.substring(startIndex).trim();
        //System.out.println(historyInfoValue);
        String[] arrHiEntry = historyInfoValue.split(Separators.COMMA);
        for (String hiEntryVal : arrHiEntry) {
            int greaterThanIndex = hiEntryVal.indexOf(Separators.GREATER_THAN);
            HiEntry hiEntry = new HiEntry();
            if (greaterThanIndex > 0) {
                hiEntry.setHiTargetedToUri(hiEntryVal.substring(0, greaterThanIndex + 1));
            }
            if (hiEntryVal.length() >= greaterThanIndex + 2) {
                String hiParamValue = hiEntryVal.substring(greaterThanIndex + 2);
                //System.out.println("hiParamValue="+hiParamValue);

                String[] arrHiParam = hiParamValue.split(Separators.SEMICOLON);
                if (arrHiParam.length >= 1) {
                    for (String item : arrHiParam) {
                        //index=1.3;rc=1.2
                        if (item.contains("=")) {
                            HiParam hiParam = new HiParam();
                            hiParam.setName(item.substring(0, item.indexOf("=")));
                            hiParam.setValue(item.substring(item.indexOf("=") + 1));
                            hiEntry.addHiParam(hiParam);
                        } else {
                            hiEntry.setHiExtension(item);
                        }
                    }
                }
            }

            historyInfoHeader.addHiEntry(hiEntry);
        }
        return historyInfoHeader;
    }

    public static String unparseHistoryInfoHeader(HistoryInfoHeader historyInfoHeader) {
        StringBuilder sb = new StringBuilder();
        sb.append(historyInfoHeader.getName() + Separators.COLON);
        for (HiEntry he : historyInfoHeader.getHiEntryList()) {
            sb.append(he.getHiTargetedToUri());

            for (HiParam hp : he.getHiParamList()) {
                sb.append(Separators.SEMICOLON);
                sb.append(hp.getName());
                sb.append(Separators.EQUALS);
                sb.append(hp.getValue());

            }
            sb.append(Separators.COMMA);
        }
        sb.deleteCharAt(sb.lastIndexOf(Separators.COMMA));
        return sb.toString();
    }

    public static Header createHistoryHeader(SipEngine engine, int reason, String nowRequestUri, String originRequestUri) throws Exception {
        return engine.headerFactory.createHeader(HISTORY_INFO_NAME, String.format(HISTORY_INFO_FORMAT, originRequestUri, reason, nowRequestUri));
    }

    public static SIPRequest createInvite(SipEngine engine, SIPRequest originRequest) throws Exception {
        SIPRequest request = B2buaHelper.createInviteRequest(engine, originRequest);
        B2buaHelper.linkRequest(originRequest, request);
        Address s_cscf = ((Route) originRequest.getRouteHeaders().getLast()).getAddress();
        request.setHeader(engine.headerFactory.createRouteHeader(s_cscf));
        return request;
    }

    public static boolean ifSupportVideoPlay(Request request) throws Exception {
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
                    return supportAmrWb(request.getRawContent());
                }
            }
        }
        return false;
    }

    public static boolean supportAmrWb(byte[] input) throws Exception {
        InputStreamReader isr = new InputStreamReader(new ByteArrayInputStream(input));
        BufferedReader bf = new BufferedReader(isr);
        String line = bf.readLine();
        while (line != null) {
            if (line.contains("a=rtpmap")) {
                // a=rtpmap:18 G729/8000
                String[] parts = line.split(" ");
                String codeName = parts[1].split("/")[0].toLowerCase();
                if (codeName.equalsIgnoreCase("amr-wb")) {
                    return true;
                }
            }
            line = bf.readLine();
        }
        return false;
    }

    public static String getHistoryInfoIndex1PhoneNum(SIPMessage message) {
        ListIterator<SIPHeader> headerIterator = message.getHeaders("history-info");
        String index1PhoneNum = null;
        while (headerIterator.hasNext()) {
            SIPHeader regularHeader = headerIterator.next();
            HistoryInfoHeader historyInfoHeader;
            try {
                historyInfoHeader = SipUtil.parseHistoryInfo(regularHeader);
            } catch (Exception e) {
                continue;
            }
            Optional<HiEntry> index1Entry = historyInfoHeader.getHiEntryList().stream().filter(SipUtil::isHiEntryContainIndex1).findFirst();
            if (index1Entry.isPresent()) {
                index1PhoneNum = parsePhoneNum(index1Entry.get());
                break;
            }
        }
        return index1PhoneNum;
    }

    public static boolean isHiEntryContainIndex1(HiEntry hiEntry) {
        List<HiParam> paramList = hiEntry.getHiParamList();
        return paramList.stream().anyMatch(hiParam -> "index".equals(StringUtils.trim(hiParam.getName())) && "1".equals(hiParam.getValue()));
    }

    private static String parsePhoneNum(HiEntry hiEntry) {
        String hiTargetedToUri = hiEntry.getHiTargetedToUri();
        if (StringUtils.isBlank(hiTargetedToUri)) {
            return null;
        }
        hiTargetedToUri = hiTargetedToUri.trim();
        String targetPhone = "";

        String[] tempArray = hiTargetedToUri.split(";");
        String targetURI = "";
        for (String item : tempArray) {
            item = item.toLowerCase();
            if (item.contains("sip:") || item.contains("tel:")) {
                targetURI = item;
                break;
            }
        }
        if (StringUtils.isNotBlank(targetURI)) {
            int index = targetURI.indexOf(":");
            if (index > 0 && targetURI.length() > index + 1) {
                targetPhone = targetURI.substring(index + 1);
            }
            index = targetPhone.indexOf("@");
            if (index > 0) {
                targetPhone = targetPhone.substring(0, index);
            }
            targetPhone = targetPhone.replaceAll("^[+:]+", "");
        }
        targetPhone = targetPhone.replace(">", "").trim();
        if (targetPhone.length() > 30) {
            targetPhone = targetPhone.substring(0, 30);
        }
        if (StringUtils.isNotBlank(targetPhone)) {
            targetPhone = SipUtil.GetPhoneNumber(targetPhone);
        }
        return targetPhone;
    }

    public static String getAreaCodeFromFirstInvite(SIPRequest request) {
        String areaCodeFromPANI = getAreaCodeFromPANI(request);
        if (PhoneSegmentCacheService.isAreaCodeValid(areaCodeFromPANI)) {
            return areaCodeFromPANI;
        }
        String areaCodeFromViaTop = getAreaCodeFromViaTop(request);
        if (StringUtils.isNotBlank(areaCodeFromViaTop)) {
            return areaCodeFromViaTop;
        }
        String areaCodeFromCaller = getAreaCodeFromCaller(request);
        if (StringUtils.isNotBlank(areaCodeFromCaller)) {
            return areaCodeFromCaller;
        }
        String areaCodeFromPAI = getAreaCodeFromPAI(request);
        if (StringUtils.isNotBlank(areaCodeFromPAI)) {
            return areaCodeFromPAI;
        }
        return "";
    }


    public static String getHomeAreaCodeFromFirstInvite(SIPRequest request) {
        try {
            String areaCodeFromViaTop = getAreaCodeFromViaTop(request);
            if (StringUtils.isNotBlank(areaCodeFromViaTop)) {
                return areaCodeFromViaTop;
            }
            String areaCodeFromCaller = getAreaCodeFromCaller(request);
            if (StringUtils.isNotBlank(areaCodeFromCaller)) {
                return areaCodeFromCaller;
            }
            return "";
        } catch (Exception e) {
            return "";
        }

    }


    private static String getAreaCodeFromPANI(SIPRequest request) {
        SIPHeader accessNetworkInfo = (SIPHeader) request.getHeader("P-Access-Network-Info");
        String areaCode = "";
        if (accessNetworkInfo != null) {
            String accessNetworkInfoValue = accessNetworkInfo.getHeaderValue();
            String[] valueArr = accessNetworkInfoValue.split(";");
            String sbcDomain = "";
            for (String value : valueArr) {
                if (value.startsWith("sbc-domain")) {
                    sbcDomain = value.split("=")[1];
                    String[] domainArr = sbcDomain.split("\\.");
                    areaCode = domainArr[1];
                    break;
                }
            }
        }
        if (areaCode.startsWith("0")) {
            areaCode = areaCode.substring(1);
        }
        return areaCode;
    }

    private static String getAreaCodeFromCaller(SIPRequest request) {
        FromHeader from = request.getFrom();
        String fromNum = SipUtil.GetPhoneNumber(from.getAddress());
        if (StringUtils.isBlank(fromNum) || fromNum.length() < 7) {
            return "";
        }
        String fromPre7 = fromNum.substring(0, 7);
        String areaCodeFromPhoneSegment = PhoneSegmentCacheService.getAreaCodeFromPhoneSegment(fromPre7);
        if (PhoneUtil.isValidMobilePhone(fromNum, fromNum) && StringUtils.isBlank(areaCodeFromPhoneSegment)){
            logger.info("phone_segment not found!, phone_segment:{}", fromPre7);
            XmodePromExporterService.getInstance().incImportantBusiness("phone_segment", fromPre7);
        }
        return areaCodeFromPhoneSegment;
    }

    /**
     * 根据初始INVITE请求中via头域的信息得到触发的icscf的地址，查询缓存信息得到icscf对应的省会城市的区号
     * @param request 一般是初始INVITE
     * @return 返回icscf对应的省会城市的区号，可能因为平台缓存的信息不全而返回空
     */
    private static String getAreaCodeFromViaTop(SIPRequest request) {
        String icscfHost = "";
        for (Via viaHeader : request.getViaHeaders()) {
            // 获取via头域中第一个不是内部添加上去的地址，如果有-cmic-out则是xsipproxy添加的，icscf地址在更后面
            if (!viaHeader.getBranch().endsWith("-cmic-out")) {
                icscfHost = viaHeader.getHost();
                break;
            }
        }
        String areaCodeByICSCF = ChinaMobileIcscfCacheService.getAreaCodeByICSCF(icscfHost);
        if (StringUtils.isBlank(areaCodeByICSCF)) {
            logger.info("areaCodeByICSCF not in db, ip:{}", icscfHost);
            XmodePromExporterService.getInstance().incImportantBusiness("icscf_ip", icscfHost);
        }
        return areaCodeByICSCF;
    }

    private static String getAreaCodeFromPAI(SIPRequest request) {
        SIPHeader pai = (SIPHeader) request.getHeader("P-Asserted-Identity");
        String provinceAbbr = "";
        if (pai != null) {
            String paiValue = pai.getHeaderValue();
            int begin = paiValue.indexOf("@");
            int end = paiValue.indexOf("mcc460.3gppnetwork.org");
            if (begin > 0 && begin < end) {
                String substring = paiValue.substring(begin + 1, end);
                String[] split = substring.split("\\.");
                provinceAbbr = split[0];
            }
        }
        if (StringUtils.isBlank(provinceAbbr)) {
            return "";
        }
        return ChinaMobileIcscfCacheService.getAreaCodeByProvinceAbbr(provinceAbbr);
    }

    public static void setNextRoute4Dcc(SIPRequest request, SIPRequest initialRequest, SipEngine engine) throws ParseException {
        //0-无代理模式 1-内外网代理模式 2-内外网代理11位手机号模拟700号码模式 3-SIPP直接压测AS模式
        if (AppConfig.getProxyMode() == 3) {
            final Address scscf = ((Route) initialRequest.getRouteHeaders().getLast()).getAddress();
            request.setHeader(engine.headerFactory.createRouteHeader(scscf));
            return;
        }
        if (AppConfig.getProxyMode() == 2) {
            final Address scscf = ((Route) initialRequest.getRouteHeaders().getLast()).getAddress();
            request.setHeader(engine.headerFactory.createRouteHeader(scscf));
            addProxyRoute(request, initialRequest, engine);
            return;
        }
        if (AppConfig.getProxyMode() == 0) {
            addViaIndexToRoute(request, initialRequest, 0, engine);
            return;
        }

        RouteList routeHeaders = initialRequest.getRouteHeaders();
        if (routeHeaders != null) {
            Header first = routeHeaders.getFirst();
            if (first.toString().contains(XSIPPROXY_TAG)) {
                // 添加index为1的via为下一跳，即icscf地址
                addViaIndexToRoute(request, initialRequest, 1, engine);
            }
        }
        if (AppConfig.getProxyMode() == 1) {
            addProxyRoute(request, initialRequest, engine);
        }
    }

    public static void setNextRoute4Gzh(SIPRequest request, SIPRequest initialRequest, SipEngine engine) throws ParseException {
        //0-无代理模式 1-内外网代理模式 2-内外网代理11位手机号模拟700号码模式 3-SIPP直接压测AS模式
        // 通过SIFC签约触发进来平台的呼叫，Route的最后一个地址是SCSCF的地址
        final Address s_cscf = ((Route) initialRequest.getRouteHeaders().getLast()).getAddress();
        request.setHeader(engine.headerFactory.createRouteHeader(s_cscf));

        if (AppConfig.getProxyMode() == 0) {
            return;
        }
        // 如果是内外网代理模式，则via的首个地址为xsipproxy的地址
        if (AppConfig.getProxyMode() == 1) {
            addProxyRoute(request, initialRequest, engine);
        }
    }

    public static void addProxyRoute(SIPRequest request, SIPRequest initSipRequest, SipEngine engine) throws ParseException {
        RouteList routeHeaders = initSipRequest.getRouteHeaders();
        if (routeHeaders != null) {
            Route first = (Route) routeHeaders.getFirst();
            if (first.toString().contains(XSIPPROXY_TAG)) {
                addViaIndexToRoute(request, initSipRequest, 0, engine);
                return;
            }
        }
        // 如果非通过代理过来的
        com.util.IPPort sipProxyHost = MyRegistryService.getInstance().getSipProxyHost();
        SipUtil.addProxyRoute(request, sipProxyHost, engine);
    }

    public static void addViaIndexToRoute(SIPRequest request, SIPRequest initialRequest, int viaIndex, SipEngine engine) throws ParseException {
        Via via = initialRequest.getViaHeaders().get(viaIndex);
        String host = via.getHost();
        int port = via.getPort();
        SipURI routeSIPURI = engine.addressFactory.createSipURI(null, host);
        routeSIPURI.setPort(port);
        routeSIPURI.setLrParam();
        Address routeAddress = engine.addressFactory.createAddress(routeSIPURI);
        RouteHeader routeHeader = engine.headerFactory.createRouteHeader(routeAddress);
        if (request.getRouteHeaders() != null && !request.getRouteHeaders().isEmpty()) {
            request.getRouteHeaders().addFirst((Route) routeHeader);
        } else {
            request.setHeader(routeHeader);
        }
    }

}
