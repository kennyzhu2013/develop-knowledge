package com.cmic.config.xml;

import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
public class ActionXml extends ObjXml {
    private String application;

    private String op;

    private String type;

    private String data;

    private List<ParamXml> params;

    private List<AttributeXml> attributes;

    public AttributeXml getAttribute(String name){
        for(AttributeXml attributeXml: attributes){
            if(attributeXml.getName().equals(name)){
                return attributeXml;
            }
        }
        return null;
    }

    public String getAttributeValue(String name){
        AttributeXml attributeXml = getAttribute(name);
        return attributeXml.getValue().trim();
    }

    public Map<String, String> getAttributeMap(){
        return attributes.stream()
                .collect(Collectors.toMap(
                        AttributeXml::getName, attr -> attr.getValue().trim(),
                        (existingVal, newVal) -> newVal));
    }
}
