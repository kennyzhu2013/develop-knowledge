package com.cmic.business.query;

import com.cmic.business.businessparam.CallParameter;
import com.cmic.sip.leg.Hook;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractStrategy implements QueryStrategy {
    Hook callQuery = null;
    private AtomicInteger callQueryRetryCount = new AtomicInteger(0);

    public final int MAX_CALL_QUERY_RETRY = 1;

    public void fireRetransCallQuery(CallParameter callParameter) throws Exception {
        if (callQueryRetryCount.get() < MAX_CALL_QUERY_RETRY) {
            callQueryRetryCount.incrementAndGet();
            callParameter.setRetryCallQuery(true);
            callQuery.apply(null);
        }
    }

    /**
     * 分机号多次转接查询,每次转接查询都要把这个值重置,此值只针对单次转接查询
     */
    protected void refreshCallQueryRetryCount() {
        callQueryRetryCount.set(0);
    }
}
