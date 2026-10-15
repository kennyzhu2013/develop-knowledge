// package com.cmic.util;
//
// import com.cmic.constant.BussinessConstant;
// import com.cmic.business.businessparam.CallParameter;
// import com.cmic.pojo.MiguRingItem;
// import net.sf.json.JSONArray;
// import net.sf.json.JSONObject;
// import org.apache.commons.lang.StringUtils;
//
// import java.util.HashMap;
// import java.util.Map;
//
// /**
//  * 高清视频、咪咕彩铃的工具类
//  * <p>
//  * Author: luohuanfa
//  * Date: 2024/1/19
//  */
// public class Video3gpUtil {
//     /**
//      * 初始化CallParameter的3gp文件map，后面play的时候会判断是否需要
//      * data数组的一项示例为：{
//      * "file":"/test",
//      * "resolution:"fb",
//      * "ringId":"690900000002000106",
//      * "type":"1"
//      * }
//      *
//      * @param checkResp
//      * @param callParameter
//      */
//     public static void initCallParameterVideo3gpFileMap(JSONObject checkResp, CallParameter callParameter, String videoBusCode, Bill bill) {
//         if ("0000".equals(videoBusCode)) {
//             Map<String, String> videoFileMap = new HashMap<>();
//             Map<String, String> videoFileRecordMap = new HashMap<>();
//             if (callParameter.getVideoRingType() == 2) { // 定制版为jsonObject
//                 JSONObject data = checkResp.getJSONObject("data");
//                 MiguRingItem ringItem = parseVideo3gpItem(data, bill);
//                 if (ringItem != null) {
//                     videoFileMap.put(ringItem.getResolution(), ringItem.getFile());
//                     videoFileRecordMap.put(ringItem.getResolution(), ringItem.getRingId() + ";" + ringItem.getRingName());
//                 }
//             } else {
//                 JSONArray data = checkResp.getJSONArray("data");
//                 if (data != null && data.size() > 0) {
//                     for (Object item : data) {
//                         MiguRingItem ringItem = parseVideo3gpItem((JSONObject) item, bill);
//                         if (ringItem != null) {
//                             videoFileMap.put(ringItem.getResolution(), ringItem.getFile());
//                             videoFileRecordMap.put(ringItem.getResolution(), ringItem.getRingId() + ";" + ringItem.getRingName());
//                         }
//                     }
//                 }
//             }
//             if (videoFileMap.size() > 0) {
//                 callParameter.setVideo3gpFileMap(videoFileMap);
//                 callParameter.setVideo3gpRecordMap(videoFileRecordMap);
//                 return;
//             }
//         }
//         DtmfLogger.info(bill.getBillId(), "init video3gpFileMap fail");
//     }
//
//     public static MiguRingItem parseVideo3gpItem(JSONObject item, Bill bill) {
//         MiguRingItem ringItem = new MiguRingItem();
//         int type = item.getInt("type");
//         if (type == 0) {
//             return null;
//         }
//         String resolution = item.getString("resolution");
//         ringItem.setResolution(resolution);
//         if (!VideoLevelRecorder.isResolutionValid(resolution)) {
//             DtmfLogger.error(bill.getBillId() + "", "invalid resolution name, stop playing 3gp!");
//             return null;
//         }
//         String ringId = item.getString("ringId");
//         ringItem.setRingId(ringId);
//         String ringName = item.getString("ringName");
//         ringItem.setRingName(ringName);
//         String file = item.getString("file");
//         ringItem.setFile(file);
//         return ringItem;
//     }
//
//     public static String buildVideo3gpCheckUrl(int videoRingType, Bill bill) {
//         if (videoRingType == BussinessConstant.VDIEO_3GP_CHECK_STANDARD_MODE) {
//             return DevHelp.VIDEO_3GP_CHECK_HOST + BussinessConstant.VDIEO_3GP_CHECK_STANDARD_URL;
//         }
//         if (videoRingType == BussinessConstant.VDIEO_3GP_CHECK_GROUPSET_MODE) {
//             return DevHelp.VIDEO_3GP_CHECK_HOST + BussinessConstant.VDIEO_3GP_CHECK_GROUPSET_URL;
//         }
//         DtmfLogger.info(bill.getBillId(), "no video3gpCheckURL when videoRingType:{} ", videoRingType);
//         return "";
//     }
//
//
//     public static boolean shouldVideo3gpCheck(CallParameter callParameter, String respVideoCode, Bill bill) {
//         if (!callParameter.isNeedVideoRing()  // 1. 不放彩铃
//                 || callParameter.getVideoRingType() == 0 // 2. 放自研彩铃
//                 || (callParameter.getVideoRingType() == 2 && StringUtils.isBlank(respVideoCode))) { // 3. 放定制彩铃，但是转接查询没有视频code
//             return false;
//         }
//         if (StringUtils.isBlank(DevHelp.VIDEO_3GP_CHECK_HOST)) {
//             DtmfLogger.info(bill.getBillId() + "", "etcdVideoCheckHost is blank, use default video setting");
//             return false;
//         }
//         return true;
//     }
// }
//
