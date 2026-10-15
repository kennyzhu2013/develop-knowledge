package com.cmic.util;

import com.mongodb.client.MongoCursor;
import org.bson.Document;
import org.springframework.util.ObjectUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class MongodbUtil {

    public static <T> List<T> convertData(MongoCursor<Document> mongoCursor, Class<T> tClass){
        List<T> list = new ArrayList<>();
        Field[] fields = tClass.getDeclaredFields();
        T t = null;
        try {
            while (mongoCursor.hasNext()) {
                t = tClass.newInstance();
                Document obj = mongoCursor.next();
                for (Field field : fields) {
                    field.setAccessible(true);
//                    if(field.isAnnotationPresent(JsonIgnore.class)){
//                        continue;
//                    }
                    String name = field.getName();
                    name = name.equals("id")?"_id":name;
                    if (!ObjectUtils.isEmpty(obj.get(name))){
                        field.set(t,obj.get(name));
                    }
                }
                list.add(t);
            }
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return list;
    }

}
