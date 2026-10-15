package com.cmic.state;

import com.cmic.BillNew;

/**
 * 精准话单模块
 * 通过提取所有信令的特征保存，最后在事务终结的时候进行状态判断
 */
public class PreciseCallState extends PreciseCallStateBase {

    public PreciseCallState(BillNew billNew) {
        super(billNew);
    }

}
