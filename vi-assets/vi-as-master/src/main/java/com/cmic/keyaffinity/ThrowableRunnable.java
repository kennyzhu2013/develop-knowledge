package com.cmic.keyaffinity;

/**
 * @author w.vela
 */
@FunctionalInterface
public interface ThrowableRunnable<X extends Throwable> {

    void run() throws X;
}
