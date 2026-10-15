package com.cmic.config.xml;

import lombok.Data;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

@Data
public class ParamXml {
    private String name;
    private String value;

    private List<AttributeXml> attributes;

    private List<ParamExcludeXml> excludes;

    private List<ParamIncludeXml> includes;

    public AttributeXml getAttribute(String name){
        for(AttributeXml attributeXml: attributes){
            if(attributeXml.getName().equals(name)){
                return attributeXml;
            }
        }
        return null;
    }

    public List<String> getIncludeList(String name){
        List<String> values = new ArrayList<>();
        if(CollectionUtils.isEmpty(includes)){
            return new ArrayList<>();
        }
        for(ParamIncludeXml includeXml: includes){
            if(includeXml.getName().equals(name)){
                values.add(includeXml.getValue());
            }
        }
        return values;
    }

    public List<String> getExcludeList(String name){
        List<String> values = new ArrayList<>();
        if(CollectionUtils.isEmpty(excludes)){
            return new ArrayList<>();
        }
        for(ParamExcludeXml excludeXml: excludes){
            if(excludeXml.getName().equals(name)){
                values.add(excludeXml.getValue());
            }
        }
        return values;
    }
}
