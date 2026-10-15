package com.cmic.config;

import lombok.Getter;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.io.File;
import java.util.List;

@Getter
public class RocketMQConfig {
    private String endpoints;

    private String ak;

    private String sk;

    public void init(String appConfigFile) throws Exception {
        SAXReader reader = new SAXReader();
        Document document = reader.read(new File(appConfigFile));
        @SuppressWarnings("unchecked")
        List<Element> listElement = document.getRootElement().elements();//所有一级子节点的list
        for (Element e : listElement) {//遍历所有一级子节点
            System.out.println("当前节点名称：" + e.getName());//当前节点名称
            System.out.println("当前节点的内容：" + e.getTextTrim());//当前节点名称

            if ("endpoints".equals(e.getName())) {
                endpoints = e.getTextTrim();
            } else if ("ak".equals(e.getName())) {
                ak = e.getTextTrim();
            } else if ("sk".equals(e.getName())) {
                sk = e.getTextTrim();
            }

        }

    }
}
