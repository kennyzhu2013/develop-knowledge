CREATE TABLE `task` (
                        `id` bigint(20) NOT NULL COMMENT '主键id',
                        `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        `extra_parameters` varchar(255) DEFAULT NULL COMMENT '其他参数',
                        `finish` tinyint(4) NOT NULL COMMENT '0-失败，1-成功',
                        `location` tinyint(4) NOT NULL COMMENT '位置：1 南，2 北; 默认南方',
                        `priority` tinyint(4) NOT NULL COMMENT 'task优先级：0,10',
                        `process_id` varchar(255) DEFAULT NULL COMMENT '流程规则id',
                        `request_id` varchar(255) DEFAULT NULL COMMENT 'client请求id',
                        `status` tinyint(4) NOT NULL COMMENT '0-未开始，1-推送中，2-子任务全部已推送完成',
                        `subtask_callback_url` varchar(255) DEFAULT NULL COMMENT '子任务回调接口的url',
                        `task_callback_url` varchar(255) DEFAULT NULL COMMENT '总任务回调接口的url',
                        `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                        PRIMARY KEY (`id`),
                        UNIQUE KEY `UK_au9ck3rs4g8f2mplbdqve6djm` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4