package com.cmic.config.xml;

import lombok.Data;

import java.util.ArrayDeque;
import java.util.Queue;

@Data
public class ConditionXml extends ObjXml {
    Queue<IfElseXml> queue = new ArrayDeque<>();
}
