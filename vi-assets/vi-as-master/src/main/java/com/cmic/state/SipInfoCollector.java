package com.cmic.state;

import com.cmic.BillNew;
import com.cmic.sip.header.historyinfo.HiEntry;
import com.cmic.sip.header.historyinfo.HiParam;
import com.cmic.sip.header.historyinfo.HistoryInfoHeader;
import com.cmic.sip.info.InfoCollector;
import com.cmic.sip.leg.Leg;
import com.cmic.util.PhoneNumUtil;
import com.cmic.util.SdpHelper;
import com.cmic.util.SipUtil;
import gov.nist.javax.sip.header.SIPHeader;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPResponse;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.SipFactory;
import javax.sip.address.AddressFactory;
import javax.sip.header.Header;
import javax.sip.header.HeaderFactory;
import javax.sip.header.ReasonHeader;
import javax.sip.header.ToHeader;
import java.sql.Timestamp;
import java.util.List;
import java.util.ListIterator;

public class SipInfoCollector extends InfoCollector {

    private static final Logger logger = LoggerFactory.getLogger(SipInfoCollector.class);
    BillNew billNew;

    public SipInfoCollector(BillNew billNew) {
        this.billNew = billNew;
    }

    /*@Override
    public int getMediaLDirection() {
        return MediaDirection.getDirection(leftMediaDirection);
    }

    @Override
    public int getMediaRDirection() {
        return MediaDirection.getDirection(rightMediaDirection);
    }*/

    @Override
    public void saveMediaDirection(Leg leg, SIPMessage sipMessage) {
        byte[] sdpContent;
        try {
            sdpContent = sipMessage.getRawContent();
            String mediaDirection = SdpHelper.getMediaDirection(sdpContent, "audio");
            //logger.info("saveMediaDirection leg isleft={},mediaDirection={}",leg.isLeft(),mediaDirection);
            if (leg.isLeft()) {
                if (!StringUtils.isEmpty(mediaDirection) && !this.leftMediaDirection.equals(mediaDirection))
                    //this.leftMediaDirection = mediaDirection;
                    billNew.setMediaLDirection(MediaDirection.getDirection(mediaDirection));
            } else {
                if (!StringUtils.isEmpty(mediaDirection) && !this.rightMediaDirection.equals(mediaDirection))
                    //this.rightMediaDirection = mediaDirection;
                    billNew.setMediaRDirection(MediaDirection.getDirection(mediaDirection));
            }

        } catch (Exception e) {
            logger.warn("saveMediaDirection billId:{}", billNew.getId(), e);
        }
    }

    @Override
    public void checkCalleeTransfer(SIPResponse sipResponse) {
        try {
            ListIterator<SIPHeader> historyInfoHeaders = sipResponse.getHeaders("History-Info");
            if (historyInfoHeaders != null && historyInfoHeaders.hasNext()) {
                ToHeader toHeader = sipResponse.getToHeader();
                String toUri = PhoneNumUtil.trimCallTarget(toHeader.getAddress().getURI().toString());
                while (historyInfoHeaders.hasNext()) {
                    HistoryInfoHeader historyInfoHeader = SipUtil.parseHistoryInfo(historyInfoHeaders.next());
                    for (HiEntry hiEntry : historyInfoHeader.getHiEntryList()) {
                        String hiTargetedToUri = hiEntry.getHiTargetedToUri();
                        if (hiTargetedToUri == null)
                            continue;

                        hiTargetedToUri = hiTargetedToUri.trim();
                        String targetPhone = "";
                        List<HiParam> paramList = hiEntry.getHiParamList();

                        long count = 0;
                        if (paramList != null)
                            count = paramList.stream().filter(hiParam -> "1.1".equalsIgnoreCase(hiParam.getValue())).count();
                        //History-Info: <tel:+8675527977558>;index=1,<tel:15013512193;cause=408>;index=1.1
                        if (StringUtils.isNotBlank(hiTargetedToUri) && (!hiTargetedToUri.contains(toUri) || count >= 1)) {
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
                                // billNew.setTransferCallee(PhoneNumUtil.getPure11NumberWithout0or86(targetPhone));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("checkCalleeTransfer exception billId={}", billNew.getId(), e);
        }
    }

    @Override
    public void saveQ850AndFirstRspTime(Leg leg, SIPResponse sipResponse) {
        try {
            int statuscode = sipResponse.getStatusCode();
            if (!leg.isLeft() && billNew.getFirstRespTime() == null) {
                billNew.setFirstRespTime(new Timestamp(System.currentTimeMillis()));
            }
            ListIterator reasonList = sipResponse.getHeaders(ReasonHeader.NAME);
            //提取消息体的Q850字段
            while (reasonList.hasNext()) {
                ReasonHeader reasonHeader = (ReasonHeader) reasonList.next();
                if ("Q.850".equalsIgnoreCase(reasonHeader.getProtocol())) {
                    int cause;
                    cause = reasonHeader.getCause();
                    if (cause < 0) {
                        continue;
                    }
                    if (cause > 65535) {
                        logger.error("Q850 cause is > 65535. billId:{}", billNew.getId());
                        cause = 65535;
                    }
                    billNew.setQ850(cause);
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("saveQ850AndFirstRspTime exception billId={}", billNew.getId(), e);
        }

    }

    private static String cleanTransferCallee(String targetCallee) throws Exception {
        if (StringUtils.isEmpty(targetCallee)) {
            return "";
        }
        String calleeStr = targetCallee.split(";")[0];
        while (calleeStr.startsWith(":") || calleeStr.startsWith("+")) {
            calleeStr = calleeStr.substring(1);
        }
        return calleeStr;
    }

    private static String testHistoryInfo(Header header) {
        HistoryInfoHeader historyInfoHeader = null;
        try {
            historyInfoHeader = SipUtil.parseHistoryInfo(header);
        } catch (Exception e) {
            e.printStackTrace();
        }
        String targetPhone = "";
        //logger.info("checkCalleeTransfer historyInfoHeader={},billId={}",historyInfoHeader.toString(),bill.getBillId());
        System.out.println(historyInfoHeader.toString());
        for (HiEntry hiEntry : historyInfoHeader.getHiEntryList()) {
            String hiTargetedToUri = hiEntry.getHiTargetedToUri();

            List<HiParam> paramList = hiEntry.getHiParamList();

            long count = 0;
            if (paramList != null) {
                count = paramList.stream().filter(hiParam -> "1.1".equalsIgnoreCase(hiParam.getValue())).count();
            }
            System.out.println(count);
            if (StringUtils.isNotBlank(hiTargetedToUri) && (!hiTargetedToUri.contains("8618100231971") || count > 1)) {
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
            }
        }
        return targetPhone;
    }

    public static void main(String[] args) {

        Header header = null;
        SipFactory sipFactory = SipFactory.getInstance();
        sipFactory.setPathName("gov.nist");
        try {
            HeaderFactory headerFactory = sipFactory.createHeaderFactory();
            AddressFactory addressFactory = sipFactory.createAddressFactory();
            System.out.println(headerFactory);
            //header = headerFactory.createHeader("History-Info","<sip:+8618931603363@he.ims.mnc011.mcc460.3gppnetwork.org?Reason=SIP%3Bcause%3D404>;index=1,<tel:+8613300320320>;index=1.1");
            //header = headerFactory.createHeader("History-Info","<sip:+8618989997580@xz.ims.mnc011.mcc460.3gppnetwork.org>;index=1,<sip:+868916343647@ims.mnc011.mcc460.3gppnetwork.org;user=phone;cause=404>;index=1.1");
            //header = headerFactory.createHeader("History-Info", "<sip:+8618100231971@10.193.61.1>;index=1,<sip:15017528288@10.193.61.1;user=phone;cause=408>;index=1.1");
            //header = headerFactory.createHeader("History-Info", "<sip:+8618100231971@10.193.61.1>;index=1,<sip:unknow@10.193.61.1;user=phone;cause=408>;index=1.1");
            //header = headerFactory.createHeader("History-Info", "<sip:+8618100231971@10.193.61.1>;index=1,<sip:unknow>;index=1.1");
            //header = headerFactory.createHeader("History-Info", "<sip:+8618100231971@10.193.61.1>;index=1,<sip: unknow;user=phone>;index=1.1");
            //header = headerFactory.createHeader("History-Info", "<sip:+8618100231971@10.193.61.1>;index=1,<sip:+unknow;user=phone>;index=1.1");
            //header = headerFactory.createHeader("History-Info", "<sip:+8618100231971@10.193.61.1>;index=1,<sip: ;user=phone>;index=1.1");

            //String targetPhone = testHistoryInfo(header);
            //System.out.println("targetPhone="+targetPhone);

            String a = "abcdef";
            if (a.length() > 5) {
                a = a.substring(0, 5);
            }
            System.out.println(a);

            //TelURL telURL = addressFactory.createTelURL("tel:unknow");

            //System.out.println(telURL.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }


    }
}
