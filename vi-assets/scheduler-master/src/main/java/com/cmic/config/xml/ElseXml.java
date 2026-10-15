package com.cmic.config.xml;

import lombok.Data;

import java.util.Queue;

@Data
public class ElseXml extends ObjXml{
    private Queue<ObjXml> queue;
}
