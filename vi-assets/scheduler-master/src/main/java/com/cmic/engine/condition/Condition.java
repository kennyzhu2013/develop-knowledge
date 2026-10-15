package com.cmic.engine.condition;

import com.cmic.config.xml.IfElseXml;
import com.cmic.engine.event.EventOrigin;

public interface Condition {
    boolean validate(IfElseXml ifElseXml);

    boolean match(IfElseXml ifElseXml, EventOrigin eventOrigin, Object... obj);
}
