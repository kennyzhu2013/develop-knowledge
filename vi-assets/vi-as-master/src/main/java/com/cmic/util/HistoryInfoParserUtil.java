package com.cmic.util;

import com.cmic.sip.header.historyinfo.HiEntry;
import com.cmic.sip.header.historyinfo.HiParam;
import com.cmic.sip.header.historyinfo.HistoryInfoHeader;
import com.cmic.sip.param.CallForward;
import com.util.PatternUtil;
import gov.nist.core.Separators;
import gov.nist.javax.sip.header.SIPHeader;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.parser.StringMsgParser;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.PeerUnavailableException;
import javax.sip.SipFactory;
import javax.sip.header.Header;
import javax.sip.header.HeaderFactory;
import javax.sip.header.ToHeader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;

/**
 * history-info头部解析util
 * <p>
 * Author: luohuanfa
 * Date: 2024/10/14
 */
public class HistoryInfoParserUtil {
    private static final Logger logger = LoggerFactory.getLogger(HistoryInfoParserUtil.class);

    public static void main(String[] args) throws ParseException {
        String sip = "INVITE sip:+8618468306529@js.ims.mnc000.mcc460.3gppnetwork.org;user=phone SIP/2.0\n" +
                "Via: SIP/2.0/UDP 10.189.102.13:5060;branch=z9hG4bK7mmi4447x77m774pm63hp9x3m;role=3;dpt=8104_16,SIP/2.0/UDP 10.193.104.168:5060;branch=z9hG4bKp9h937mxq1piixo6f3fhh63xmFFAS;received=10.193.104.169,SIP/2.0/UDP 10.189.102.13:5060;branch=z9hG4bKp9h937mxq1piixo6f3fhh63xm;role=3;dpt=8104_16,SIP/2.0/UDP 10.189.102.22:5060;branch=z9hG4bKykibssiitfndniykkkethgxgx;role=3;pth=0;dpt=7e26_16,SIP/2.0/UDP 10.189.102.23:5060;branch=z9hG4bKmjemjfxdgfnmjtimgesjddlbf;role=3;dpt=7e24_16,SIP/2.0/UDP 10.189.102.175:5060;branch=z9hG4bKpcz0cb09uybcbybud98pubaps;role=3;dpt=ec8a_16\n" +
                "Route: <sip:neibubfhdhzy2cshaeb.hdh.chinamobile.com;lr>,<sip:10.189.102.13;lr;ORGDLGID=9922-10;Dpt=8104_6>\n" +
                "Record-Route: <sip:10.189.102.13;lr;Dpt=8104_516;Role=3;CxtId=4;spln=S;X-HwB2bUaCookie=5145>,<sip:10.193.104.168:5060;lr>,<sip:10.189.102.13;lr;CxtId=4;Role=3;Dpt=8104_316;spln=S;X-HwB2bUaCookie=23500>,<sip:10.189.102.23;lr;CxtId=4;Role=3;Dpt=7e24_116;spln=S;X-HwB2bUaCookie=8911>,<sip:10.189.102.175:5060;lr;CxtId=4;Role=3;Dpt=ec8a_16;X-HwB2bUaCookie=14858>\n" +
                "Call-ID: tdc8cacepbzdbrhbbshbd9u0ascs0pzs@10.189.102.174\n" +
                "From: \"+8615852831214\" <tel:+8615852831214>;tag=ruduazhu\n" +
                "To: <tel:18468306529;phone-context=ims.mnc002.mcc460.3gppnetwork.org>\n" +
                "CSeq: 1062220216 INVITE\n" +
                "Accept: application/sdp,application/3gpp-ims+xml\n" +
                "Allow: INVITE,ACK,CANCEL,BYE,UPDATE,PRACK,MESSAGE,REFER,NOTIFY,INFO,OPTIONS\n" +
                "Contact: <sip:10.189.102.175:5060;Dpt=ec8a-200>;+sip.instance=\"<urn:gsma:imei:86474505-779560-0>\";video;+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.mmtel\"\n" +
                "Max-Forwards: 60\n" +
                "Supported: timer,100rel,replaces,precondition,histinfo,tdialog\n" +
                "User-Agent: vivo_V2055A_PD2055_A_6.9.11\n" +
                "Session-Expires: 1800\n" +
                "Min-SE: 90\n" +
                "History-Info: <sip:17288476558@10.191.96.4:5060;transport=udp;user=phone?reason=SIP%3Bcause%3D302%3Btext%3D%22Unconditional%22&Privacy=none>;index=1,<sip:+8618468306529@10.184.205.101:5060;transport=udp;user=phone;cause=305>;index=1.1\n"+
                //"History-Info: <sip:+8615915925936@gd.ims.mnc000.mcc460.3gppnetwork.org?Reason=SIP%3Bcause%3D303>;index=1,<sip:+8614749437919@gd.ims.mnc000.mcc460.3gppnetwork.org;user=phone;cause=305>;index=1.1\n"+
                "P-Asserted-Identity: \"+8615852831214\" <sip:+8615852831214@js.ims.mnc000.mcc460.3gppnetwork.org>,\"+8615852831214\" <tel:+8615852831214>\n" +
                "P-Visited-Network-ID: \"wuxpsbc3bhw.js.ims.mnc000.mcc460.3gppnetwork.org\"\n" +
                "P-Access-Network-Info: 3GPP-E-UTRAN; utran-cell-id-3gpp=460006215520D816;sbc-domain=wxsbc3.0510.101.js.chinamobile.com;ue-ip=[2409:8121:1840:92D7:8153:EFAD:5641:770F];ue-port=43703;network-provided\n" +
                "P-Charging-Vector: icid-value=\"wuxpsbc3bhw.195.6258.20220718074919\";orig-ioi=js.chinamobile.com;term-ioi=js.chinamobile.com\n" +
                "P-Early-Media: supported\n" +
                "P-Preferred-Service: urn:urn-7:3gpp-service.ims.icsi.mmtel\n" +
                "Accept-Contact: *;+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.mmtel\"\n" +
                "Content-Type: application/sdp\n" +
                "Content-Length: 905\n" +
                "\n" +
                "v=0\n" +
                "o=HuaweiATS9900 677567962 677567962 IN IP4 10.189.102.174\n" +
                "s=SBC call\n" +
                "c=IN IP4 10.190.23.1\n" +
                "b=AS:49\n" +
                "b=RS:600\n" +
                "b=RR:2000\n" +
                "t=0 0\n" +
                "m=audio 42902 RTP/AVP 104 102 96 97 111 106 18 8 0\n" +
                "b=AS:82\n" +
                "b=RS:600\n" +
                "b=RR:2000\n" +
                "a=rtpmap:104 AMR-WB/16000/1\n" +
                "a=fmtp:104 mode-change-capability=2;max-red=0\n" +
                "a=rtpmap:102 AMR/8000/1\n" +
                "a=fmtp:102 mode-change-capability=2;max-red=0\n" +
                "a=rtpmap:96 telephone-event/16000\n" +
                "a=fmtp:96 0-15\n" +
                "a=rtpmap:97 telephone-event/8000\n" +
                "a=fmtp:97 0-15\n" +
                "a=curr:qos local none\n" +
                "a=curr:qos remote none\n" +
                "a=des:qos mandatory local sendrecv\n" +
                "a=des:qos optional remote sendrecv\n" +
                "a=sendrecv\n" +
                "a=maxptime:240\n" +
                "a=ptime:20\n" +
                "a=rtpmap:111 EVS/16000\n" +
                "a=fmtp:111 br=9.6-64;bw=swb;mode-change-capability=2;cmr=0;dtx=0;ch-aw-recv=-1;max-red=0\n" +
                "a=rtpmap:106 EVS/16000\n" +
                "a=fmtp:106 br=5.9-64;bw=wb;mode-change-capability=2;cmr=0;dtx=0;ch-aw-recv=-1;max-red=0\n" +
                "a=rtpmap:18 G729/8000\n" +
                "a=fmtp:18 annexb=no\n" +
                "a=rtpmap:8 PCMA/8000\n" +
                "a=rtpmap:0 PCMU/8000\n";
        StringMsgParser sp = new StringMsgParser();
        SIPMessage sipMessage = sp.parseSIPMessage(sip.getBytes(), true, false, null);
        //String historyInfoIndex1PhoneNum = getHistoryInfoIndex1PhoneNum((SIPRequest) sipMessage);
        //System.out.println(historyInfoIndex1PhoneNum);
        //System.out.println(historyInfoIndex1PhoneNum == null);
        CallForward callForward = extractCallForwardInfo(sipMessage, false);
        System.out.println(callForward.toString());
    }


    public static String getHistoryInfoIndex1PhoneNum(SIPRequest request) {
        String index1PhoneNum = null;
        try {
            ListIterator<SIPHeader> headerIterator = request.getHeaders("history-info");
            while (headerIterator.hasNext()) {
                SIPHeader regularHeader = headerIterator.next();
                HistoryInfoHeader historyInfoHeader;
                try {
                    historyInfoHeader = SipUtil.parseHistoryInfo(regularHeader);
                } catch (Exception e) {
                    continue;
                }
                Optional<HiEntry> index1Entry = historyInfoHeader.getHiEntryList().stream().filter(HistoryInfoParserUtil::isHiEntryContainIndex1).findFirst();
                if (index1Entry.isPresent()) {
                    index1PhoneNum = parsePhoneNum(index1Entry.get());
                    break;
                }
            }
        } catch (Exception e) {
            logger.info("[{}], [HistoryInfoParserUtil-getHistoryInfoIndex1PhoneNum], history-info parse error!", request.getCallId(), e);
            return "";
        }
        return index1PhoneNum;
    }

    private static boolean isHiEntryContainIndex1(HiEntry hiEntry) {
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
            targetPhone = PhoneNumUtil.getPure11NumberWithout0or86(targetPhone);
        }
        return targetPhone;
    }

    public static HistoryInfoHeader parseHistoryInfo(Header historyInfo) {
        String historyInfoStr = historyInfo.toString();
        HistoryInfoHeader historyInfoHeader = new HistoryInfoHeader();
        int startIndex = "History-Info:".length();
        String historyInfoValue = historyInfoStr.substring(startIndex).trim();
        String[] arrHiEntry = historyInfoValue.split(Separators.COMMA);
        for (String hiEntryVal : arrHiEntry) {
            int greaterThanIndex = hiEntryVal.indexOf(Separators.GREATER_THAN);
            HiEntry hiEntry = new HiEntry();
            if (greaterThanIndex > 0) {
                hiEntry.setHiTargetedToUri(hiEntryVal.substring(0, greaterThanIndex + 1));
            }
            String hiParamValue = hiEntryVal.substring(greaterThanIndex + 2);

            String[] arrHiParam = hiParamValue.split(Separators.SEMICOLON);
            if (arrHiParam.length >= 1) {
                for (String item : arrHiParam) {
                    // index=1.3;rc=1.2
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
            historyInfoHeader.addHiEntry(hiEntry);
        }
        return historyInfoHeader;
    }


    /**
     * History-Info: <tel:15344452022>;index=1,<tel:+8613487314616;unconditional>;index=1.1
     * History-Info: <sip:13145074359@anonymous.invalid;user=phone?reason=SIP%3Bcause%3D302%3Btext%3D%22Unconditional%22&Privacy=history>;index=1,<sip:+8615784504661@10.59.110.98:5060;transport=udp;user=phone>;index=1.1
     * History-Info: <sip:+8617863136922@sd.ims.mnc000.mcc460.3gppnetwork.org?Reason=SIP%3Bcause%3D302>;index=1,<tel:+8617860081865>;index=1.1
     * History-Info: <sip:17283047446@10.184.2.30:5060;transport=udp;user=phone?reason=SIP%3Bcause%3D302%3Btext%3D%22Unconditional%22&Privacy=none>;index=1,<sip:+8617283047446@10.69.231.53:5060;transport=udp;user=phone>;index=1.1
     * History-Info: <tel:15344452022>;index=1,<tel:+8613487314616;unconditional>;index=1.1
     * History-Info: <sip:13145074359@anonymous.invalid;user=phone?reason=SIP%3Bcause%3D302%3Btext%3D%22Unconditional%22&Privacy=history>;index=1,<sip:+8615784504661@10.59.110.98:5060;transport=udp;user=phone>;index=1.1
     * History-Info: <sip:17898952104@10.191.96.4:5060;transport=udp;user=phone?reason=SIP%3Bcause%3D302%3Btext%3D%22Unconditional%22&Privacy=none>;index=1,<sip:+8617898952104@10.184.205.101:5060;transport=udp;user=phone>;index=1.1
     * History-Info: <tel:+8618705612439>;index=1,<sip:+8617856118925@ah.chinamobile.com;user=phone;cause=302>;index=1.1
     * History-Info: <tel:+8618705612439>;index=1,<sip:+8617856118925@ah.chinamobile.com;user=phone;cause=302>;index=1.1
     * History-Info: <sip:+8615915925936@gd.ims.mnc000.mcc460.3gppnetwork.org?Reason=SIP%3Bcause%3D302>;index=1,<sip:+8614749437919@gd.ims.mnc000.mcc460.3gppnetwork.org;user=phone;cause=302>;index=1.1
     * History-Info: <sip:+8618765929391@sd.ims.mnc000.mcc460.3gppnetwork.org?Reason=SIP%3Bcause%3D302>;index=1,<tel:+8615006583390>;index=1.1
     * History-Info: <sip:+8618765929391@sd.ims.mnc000.mcc460.3gppnetwork.org?Reason=SIP%3Bcause%3D302>;index=1,<tel:+8615006583390>;index=1.1
     * History-Info: <tel:+8618831268287>;index=1,<sip:+8618831200867@he.chinamobile.com;cause=302;user=phone>;index=1.1
     * History-Info: <tel:+8618831268287>;index=1,<sip:+8618831200867@he.chinamobile.com;cause=302;user=phone>;index=1.1
     */

    public static CallForward extractCallForwardInfo(SIPMessage sipMessage,boolean excludeCallee) {
        CallForward callForward = new CallForward();
        try {
            ListIterator<SIPHeader> historyInfoHeaders = sipMessage.getHeaders("History-Info");
            if (historyInfoHeaders != null && historyInfoHeaders.hasNext()) {
                String callForwardCause = "";
                boolean isUnconditional =  false;
                ToHeader toHeader = sipMessage.getToHeader();
                String toUri = PhoneNumUtil.trimCallTarget(toHeader.getAddress().getURI().toString());
                String[] arrToUri = toUri.split(";");
                String toPhoneNo = arrToUri[0];
                while (historyInfoHeaders.hasNext()) {
                    HistoryInfoHeader historyInfoHeader = SipUtil.parseHistoryInfo(historyInfoHeaders.next());
                    String newcallForwardCause = "";
                    String preCallForwardCause = "";
                    boolean newIsUnconditional = false;
                    boolean preIsUnconditional = false;
                    for (HiEntry hiEntry : historyInfoHeader.getHiEntryList()) {
                        preCallForwardCause = newcallForwardCause;
                        preIsUnconditional = newIsUnconditional;
                        newcallForwardCause = "";
                        newIsUnconditional = false;
                        String hiTargetedToUri = hiEntry.getHiTargetedToUri();
                        if (StringUtils.isBlank(hiTargetedToUri))
                            continue;
                        String decodedUri = "";
                        hiTargetedToUri = hiTargetedToUri.trim();
                        if(StringUtils.isNotBlank(hiTargetedToUri)){
                            // URL 解码，处理 %3B 等编码字符
                            decodedUri = URLDecoder.decode(hiTargetedToUri, StandardCharsets.UTF_8);
                            if(StringUtils.isBlank(decodedUri)){
                                continue;
                            }
                            if(decodedUri.contains("Unconditional")){
                                newIsUnconditional = true;
                            }
                            if(decodedUri.contains("cause=")){
                                newcallForwardCause = PatternUtil.getCause(decodedUri);
                            }
                        }

                        if(excludeCallee && hiTargetedToUri.contains(toPhoneNo)){
                            newcallForwardCause = preCallForwardCause;
                            newIsUnconditional = preIsUnconditional;
                            continue;
                        }

                        boolean toExtract = !excludeCallee || !hiTargetedToUri.contains(toPhoneNo);
                        String targetPhone = "";
                        List<HiParam> paramList = hiEntry.getHiParamList();
                        long count = 0;
                        if (paramList != null)
                            count = paramList.stream().filter(hiParam -> "1.1".equalsIgnoreCase(hiParam.getValue())).count();
                        if (toExtract || count >= 1) {
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
                                while (targetPhone.startsWith("+") || targetPhone.startsWith(":")) {
                                    targetPhone = targetPhone.substring(1);
                                }
                            }
                            targetPhone = targetPhone.replace(">", "");
                            targetPhone = targetPhone.trim();
                            if (targetPhone.length() > 30) {
                                targetPhone = targetPhone.substring(0, 30);
                            }
                            if (StringUtils.isNotBlank(targetPhone)) {
                                callForward.setCallForwardNo(PhoneNumUtil.getPure11NumberWithout0or86(targetPhone));
                            }
                        }
                    }
                    if(StringUtils.isNotBlank(newcallForwardCause)){
                        callForwardCause = newcallForwardCause;
                    }
                    isUnconditional = newIsUnconditional;
                }
                callForward.setCallForwardCause(callForwardCause);
                callForward.setUnconditional(isUnconditional);
            }
        } catch (Exception e) {
            DtmfLogger.error(String.valueOf(sipMessage.getCallId()), "extractCallForwardInfo exception",e);
        }
        return callForward;
    }






}
