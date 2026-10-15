package com.cmic;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SomeService {

    public String processMap(List list) {
        // 对传入的Map进行处理
        // 这里简单地返回Map的字符串表示形式作为示例
        return list.toString();
    }
}
