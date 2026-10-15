CREATE TABLE `smart_walnuts_sub_task` (
                                          `id` bigint NOT NULL COMMENT '主键id',
                                          `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                                          `error_message` varchar(255) DEFAULT NULL COMMENT '错误信息',
                                          `file_id` varchar(255) DEFAULT NULL COMMENT '客户提供的文件id',
                                          `file_url` varchar(255) DEFAULT NULL COMMENT '录音文件下载url',
                                          `finish` tinyint NOT NULL COMMENT '0-失败，1-成功',
                                          `status` tinyint NOT NULL COMMENT '子任务推送 MQ 状态 0-未开始 1-推送成功 2-推送失败',
                                          `task_id` bigint NOT NULL COMMENT '关联主任务id',
                                          `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                          `location` tinyint NOT NULL DEFAULT '1' COMMENT '1-南方，2-北方',
                                          PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4