package com.cmic.util;

import cn.hutool.json.JSONObject;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Map;

@Slf4j
public class JacksonUtil {

    private static ObjectMapper objectMapper;
    // 日起格式化
    private static final String STANDARD_FORMAT = "yyyy-MM-dd HH:mm:ss";

    static {
        objectMapper = new ObjectMapper();
        //忽略位置字段，防止报错
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        //忽略字段大小写
        objectMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
        //取消默认转换timestamps形式
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,false);
        //忽略空Bean转json的错误
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS,false);
        //所有的日期格式都统一为以下的样式，即yyyy-MM-dd HH:mm:ss
        objectMapper.setDateFormat(new SimpleDateFormat(STANDARD_FORMAT));
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    /**
     * 对象转Json格式字符串
     * @param obj 对象
     * @return Json格式字符串
     */
    public static <T> String obj2String(T obj) {
        if (obj == null) {
            return null;
        }
        try {
            return obj instanceof String ? (String) obj : objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Parse Object to String error : {}", e.getMessage());
            return null;
        }
    }

    /**
     * 对象转Json格式字符串(格式化的Json字符串)
     * @param obj 对象
     * @return 美化的Json格式字符串
     */
    public static <T> String obj2StringPretty(T obj) {
        if (obj == null) {
            return null;
        }
        try {
            return obj instanceof String ? (String) obj : objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Parse Object to String error : {}", e.getMessage());
            return null;
        }
    }

    /**
     * 字符串转换为自定义对象
     * @param str 要转换的字符串
     * @param clazz 自定义对象的class对象
     * @return 自定义对象
     */
    public static <T> T string2Obj(String str, Class<T> clazz){
        if(StringUtils.isEmpty(str) || clazz == null){
            return null;
        }
        try {
            return clazz.equals(String.class) ? (T) str : objectMapper.readValue(str, clazz);
        } catch (Exception e) {
            log.warn("Parse String to Object error : {}", e.getMessage());
            return null;
        }
    }

    public static <T> T string2Obj(String str, TypeReference<T> typeReference) {
        if (StringUtils.isEmpty(str) || typeReference == null) {
            return null;
        }
        try {
            return (T) (typeReference.getType().equals(String.class) ? str : objectMapper.readValue(str, typeReference));
        } catch (IOException e) {
            log.warn("Parse String to Object error", e);
            return null;
        }
    }

    public static <T> T string2Obj(String str, Class<?> collectionClazz, Class<?>... elementClazzes) {
        JavaType javaType = objectMapper.getTypeFactory().constructParametricType(collectionClazz, elementClazzes);
        try {
            return objectMapper.readValue(str, javaType);
        } catch (IOException e) {
            log.warn("Parse String to Object error : {}" + e.getMessage());
            return null;
        }
    }

    public static <T> T mapToBean(Map map, Class clazz) {
        return (T) objectMapper.convertValue(map, clazz);
    }

    public static Map beanToMap(Object obj){
        return objectMapper.convertValue(obj, Map.class);
    }

    public static String createJson(String newAttribute, Object newValue){
        ObjectNode jsonObj = objectMapper.createObjectNode();
        jsonObj.putPOJO(newAttribute, newValue);
        return jsonObj.toString();
    }

    public static String addJsonAttribute(String targetJson, String newAttribute, Object newValue) throws JsonProcessingException {
        ObjectNode jsonObj = (ObjectNode) objectMapper.readTree(targetJson);
        jsonObj.putPOJO(newAttribute, newValue);
        return jsonObj.toString();
    }

    public static JSONObject addJsonAttribute(JSONObject targetJson, String newAttribute, Object newValue) throws JsonProcessingException {
        targetJson.set(newAttribute, newValue);
        return targetJson;
    }

}
