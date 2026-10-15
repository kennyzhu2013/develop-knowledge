package com.cmic.engine.action;

import com.cmic.config.xml.ActionXml;
import com.cmic.engine.event.EventOrigin;

public interface Action {
    void validate(ActionXml obj) throws Exception;

    void apply(ActionXml actionXml, EventOrigin eventOrigin, Object... obj) throws Exception;
}
