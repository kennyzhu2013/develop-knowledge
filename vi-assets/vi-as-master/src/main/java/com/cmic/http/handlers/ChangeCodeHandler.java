package com.cmic.http.handlers;

import com.cmic.service.ScheduleService;
import com.cmic.util.DevHelp;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import net.sf.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyDescriptor;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author XXXXX
 * @date 2023/07/21
 */
public class ChangeCodeHandler implements HttpHandler {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody()));
        StringBuilder params = new StringBuilder();
        String s = null;
        while ((s = reader.readLine()) != null) {
            params.append(s);
        }

        JSONObject req = JSONObject.fromObject(params.toString());
        Class<?> clazz;
        try {
            clazz = Class.forName("com.cmic.util.DevHelp");
            Field[] fields = clazz.getDeclaredFields();
            PropertyDescriptor propertyDescriptor;
            for (Field field : fields) {
                String fieldName = field.getName();
                if (req.containsKey(fieldName)) {
                    propertyDescriptor = new PropertyDescriptor(fieldName, clazz);
                    Method propertyWriteMethod = propertyDescriptor.getWriteMethod();
                    propertyWriteMethod.invoke(new DevHelp(), req.get(fieldName));
                    logger.info("=======set DevHelp.{} to {}========", fieldName, req.get(fieldName));
                }
            }
        } catch (Exception e) {
            logger.error("Error happen while setting DevHelp property", e);
            throw new RuntimeException(e);
        }
        // if (req.containsKey("forceAsr")) {
        //     forceAsr = req.getString("forceAsr");
        //     DevHelp.setForceAsr(forceAsr);
        //     logger.info("=======设置了DevHelp的forceAsr为========" + forceAsr);
        // }
        // if (req.containsKey("forceCodec")) {
        //     forceCodec = req.getString("forceCodec");
        //     DevHelp.setForceCodec(forceCodec);
        //     logger.info("=======设置了DevHelp的forceCodec为========" + forceCodec);
        // }
        // if (req.containsKey("forceInBand")) {
        //     forceInBand = "1".equalsIgnoreCase(req.getString("forceInBand").trim());
        //     DevHelp.setForceInBand(forceInBand);
        //     logger.info("=======设置了DevHelp的forceInBand为========" + forceInBand);
        // }
        // if (req.containsKey("sleepTimeBetweenDtmf")) {
        //     long sleepTimeBetweenDtmf = req.getLong("sleepTimeBetweenDtmf");
        //     DevHelp.setSleepTimeBetweenDtmf(sleepTimeBetweenDtmf);
        //     logger.info("=======设置了DevHelp的sleepTimeBetweenDtmf为========" + sleepTimeBetweenDtmf);
        // }
        String id = UUID.randomUUID().toString().replaceAll("-", "");
        DevHelp.setId(id);
        // 一小时后复原
        ScheduleService.getInstance().schedule(() -> {
            // 如果不一致，说明后续执行了其他操作，因而不能修改，得最后一条命令（DevHelp.ID与最后一条命令的ID保持一致）才能
            if (DevHelp.id.equalsIgnoreCase(id)) {
                DevHelp.setForceAsr("0");
                DevHelp.setForceCodec("");
            }
        }, 60, TimeUnit.MINUTES);
        exchange.sendResponseHeaders(200, 0);
        OutputStream os = exchange.getResponseBody();
        os.write("ok".getBytes());
        os.close();
    }
}
