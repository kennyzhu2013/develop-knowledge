package com.cmic.util;

import com.dcc.config.model.SpecialCallRegulationConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * @author liangyuhao
 * @title PrefixTries
 * @date 2025/12/10 10:54
 * @description special_callee_regulation表的前缀匹配的数据用前缀树来存储
 */
public class SpecialCallRegulationPrefixTries {
    private TrieNode root = new TrieNode();

    private static class TrieNode {
        HashMap<Integer, SpecialCallRegulationConfig> configMap;
        Map<Character, TrieNode> children = new HashMap<>();
        boolean isEnd;
    }

    public void insert(String prefix, int province, SpecialCallRegulationConfig config){
        TrieNode node = root;
        for (char c:prefix.toCharArray()){
            if(!node.children.containsKey(c)){
                TrieNode newNode = new TrieNode();
                node.children.put(c,newNode);
            }
            node = node.children.get(c);
        }
        if (node.configMap == null){
            node.configMap = new HashMap<>();
        }
        node.configMap.put(province, config);
        node.isEnd = true;
    }

    //前缀匹配优先匹配较长的号段的数据
    public HashMap<Integer, SpecialCallRegulationConfig> find(String number){
        TrieNode node = root;
        HashMap<Integer, SpecialCallRegulationConfig> bestResult = null;
        for (char c:number.toCharArray()){
            if (!node.children.containsKey(c)){
                break;
            }
            node = node.children.get(c);
            if (node.isEnd && node.configMap != null){
                bestResult = node.configMap;
            }
        }
        return bestResult;
    }
}
