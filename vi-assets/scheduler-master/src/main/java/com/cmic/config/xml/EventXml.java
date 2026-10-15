package com.cmic.config.xml;

import lombok.Data;

import java.util.Queue;

@Data
public class EventXml {
    private String name;
    private Queue<ObjXml> queue;
}
