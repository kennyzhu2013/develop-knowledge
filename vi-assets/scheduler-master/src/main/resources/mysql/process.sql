CREATE TABLE `process` (
                           `id` bigint(20) NOT NULL AUTO_INCREMENT,
                           `create_time` datetime(6) DEFAULT NULL,
                           `process_id` varchar(255) DEFAULT NULL,
                           `update_time` datetime(6) DEFAULT NULL,
                           `xml_file_name` varchar(255) DEFAULT NULL,
                           `timeout` int(11) DEFAULT '0' COMMENT '超时时间',
                           `ai_models` varchar(255) DEFAULT NULL COMMENT 'AI模型，用'',''隔开',
                           PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4