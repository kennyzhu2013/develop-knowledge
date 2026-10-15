package com.cmic.recording;


@FunctionalInterface
public interface  MediaHook <T>{
      void apply(T t) throws Exception;

}