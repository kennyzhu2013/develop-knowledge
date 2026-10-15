package com.cmic.sip.leg;

import java.util.Objects;

@FunctionalInterface
public interface Hook<T> {
    void apply(T event) throws Exception;

    /** before将在this前面执行 **/
    default Hook<T> addBefore(Hook<? super T> before) {
        Objects.requireNonNull(before);
        return (T t) -> {
            before.apply(t);
            apply(t);
        };
    }

    default Hook<T> andThen(Hook<? super T> after) {
        Objects.requireNonNull(after);
        return (T t) -> {
            apply(t);
            after.apply(t);
        };
    }
}