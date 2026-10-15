package com.cmic.sip.leg;

/**
 * 消息内容装饰（request、response）
 **/
@FunctionalInterface
public interface DecorateMessage<T> {
    void apply(T message) throws Exception;
}