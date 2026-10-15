package com.cmic.sip.header.historyinfo;

import com.cmic.util.SipUtil;

import javax.sip.PeerUnavailableException;
import javax.sip.SipFactory;
import javax.sip.header.Header;
import javax.sip.header.HeaderFactory;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

public class HistoryInfoHeader implements Header {

    private List<HiEntry> hiEntryList = new ArrayList<HiEntry>();

    @Override
    public String getName() {
        return "History-Info";
    }


    @Override
    public Object clone() {
        return null;
    }

    public void addHiEntry(HiEntry hiEntry){
        hiEntryList.add(hiEntry);
    }

    public List<HiEntry> getHiEntryList(){
        return hiEntryList;
    }

    @Override
    public String toString() {
        return "HistoryInfoHeader{" +
                "hiEntryList=" + hiEntryList +
                '}';
    }

    public static void main(String[] args) {
        Header header = null;
        SipFactory sipFactory = SipFactory.getInstance();
        sipFactory.setPathName("gov.nist");
        try {
             HeaderFactory headerFactory = sipFactory.createHeaderFactory();
            System.out.println(headerFactory);
            //header = headerFactory.createHeader("History-Info","<sip:+8618931603363@he.ims.mnc011.mcc460.3gppnetwork.org?Reason=SIP%3Bcause%3D404>;index=1,<tel:+8613300320320>;index=1.1");
            //header = headerFactory.createHeader("History-Info","<sip:+8618989997580@xz.ims.mnc011.mcc460.3gppnetwork.org>;index=1,<sip:+868916343647@ims.mnc011.mcc460.3gppnetwork.org;user=phone;cause=404>;index=1.1");
            //header = headerFactory.createHeader("History-Info","<sip:+8618100231971@10.193.61.1>;index=1,<sip:15017528288@10.193.61.1;user=phone;cause=408>;index=1.1");
            header = headerFactory.createHeader("History-Info","<sip:13145074359@anonymous.invalid;user=phone?reason=SIP%3Bcause%3D302%3Btext%3D%22Unconditional%22&Privacy=history>;index=1,<sip:+8615784504661@10.59.110.98:5060;transport=udp;user=phone>;index=1.1");
            //header = headerFactory.createHeader("History-Info","<sip:+8615915925936@gd.ims.mnc000.mcc460.3gppnetwork.org?Reason=SIP%3Bcause%3D302>;index=1,<sip:+8614749437919@gd.ims.mnc000.mcc460.3gppnetwork.org;user=phone;cause=302>;index=1.1");
            System.out.println(header.toString());

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

            HistoryInfoHeader historyInfoHeader = SipUtil.parseHistoryInfo(header);
            System.out.println("===========================================");
            System.out.println(historyInfoHeader.toString());

            List<HiEntry> hiEntryList = historyInfoHeader.getHiEntryList();
            // 遍历所有的 HiEntry
            List<HiEntry> hiEntries = historyInfoHeader.getHiEntryList();
            String causeValue = "";
            for (HiEntry hiEntry : hiEntries) {
                String hiTargetedToUri = hiEntry.getHiTargetedToUri();
                // URL 解码，处理 %3B 等编码字符
                String decodedUri = URLDecoder.decode(hiTargetedToUri, StandardCharsets.UTF_8);
                System.out.println("decodedUri: "+decodedUri);
                // 按分号分割参数
                String[] items = decodedUri.split(";");
                for (String item : items) {
                    // 查找 cause 参数
                    if (item.trim().startsWith("cause=")) {
                        causeValue = item.substring(item.indexOf("=") + 1).trim();
                        // 移除可能存在的结束符号">"
                        if (causeValue.endsWith(">")) {
                            causeValue = causeValue.substring(0, causeValue.length() - 1);
                        }
                    }
                }
            }
            System.out.println(causeValue);




        } catch (PeerUnavailableException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }





}
