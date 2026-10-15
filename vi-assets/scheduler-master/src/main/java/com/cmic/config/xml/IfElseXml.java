package com.cmic.config.xml;

import lombok.Data;

import java.util.Queue;

@Data
public class IfElseXml extends ObjXml{
    private String application = "default";

    private String expression;

    private Queue<ObjXml> queue;
}
