package com.cmic.bizcontroller;


@FunctionalInterface
public interface DoPrepareParameterCallBack<T> {
    void apply(T var1);
}

