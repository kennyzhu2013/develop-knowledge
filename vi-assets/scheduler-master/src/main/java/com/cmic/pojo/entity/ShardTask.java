package com.cmic.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "shard_task")
public class ShardTask {
    @Id
    private String id;

    private String callId;

    /**
     * asr全文本
     */
    private String asrText;
    /**
     * ASR总时长
     */
    private Long asrDuration = 0L;

    private boolean interruptFlag = false;

    /**
     * 历史分片信息
     */
    private Map<Integer, SharkInfo> sharkInfoMap = new HashMap<>();

    @Data
    public static class SharkInfo {
        /**
         * 子任务ID
         */
        private Long subTaskId;

        /**
         * 分片ID
         */
        private int segmentId;
        /**
         * 是否最后一个分片
         */
        private boolean endFlag = false;

        /**
         * 是否已收到
         */
        private boolean received = false;

        /**
         * 是否已经执行
         */
//        private boolean process = false;

        /**
         * 是否已完成
         */
        private boolean finish = false;

        /**
         * 分片对应的asr内容
         */
        private String asr;
    }
}

