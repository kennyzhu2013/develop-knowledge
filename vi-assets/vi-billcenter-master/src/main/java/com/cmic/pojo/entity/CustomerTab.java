package com.cmic.pojo.entity;

import lombok.Data;

import java.util.Date;

@Data
public class CustomerTab {
/**
 * id` int NOT NULL AUTO_INCREMENT COMMENT '主键id',
 *   `name` varchar(100) NOT NULL COMMENT '企业英文名（可用拼音代替）',
 *   `nickname` varchar(100) NOT NULL DEFAULT '' COMMENT '企业中文简称',
 *   `fullname` varchar(100) NOT NULL COMMENT '企业中文全称',
 *   `parent_id` int NOT NULL COMMENT '父级企业ID',
 *   `status` tinyint(1) NOT NULL DEFAULT '3' COMMENT '状态，枚举值：1-启用、2-停用、3-注销',
 *   `ability` tinyint(1) NOT NULL COMMENT '关联原子能力，离线asr-1，实时asr-2，质检-3',
 *   `role_id_list` varchar(100) DEFAULT '' COMMENT '关联角色id列表，以逗号分隔',
 *   `creator` varchar(50) NOT NULL DEFAULT '' COMMENT '创建者',
 *   `comment` varchar(100) DEFAULT '' COMMENT '备注',
 *   `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 *   `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
 *   `ability_status` tinyint NOT NULL DEFAULT '1' COMMENT '能力状态，1-可用，0-不可用',
 *   `app_id` varchar(100) NOT NULL COMMENT '终端ID',
 *   `platform_app_id` varchar(20) NOT NULL DEFAULT 'retention' COMMENT '能开平台对应的appId',
 *   `priority` int NOT NULL COMMENT '优先级',
 *   `url` varchar(255) NOT NULL DEFAULT '' COMMENT '质检复核回调地址',
 *   `is_reuse_asr` tinyint NOT NULL DEFAULT '0' COMMENT '是否复用历史ASR文本 1-是 0-否',
 *   `source` tinyint NOT NULL DEFAULT '2' COMMENT '1-能开;2-后台添加',
 *   `customer_type` int NOT NULL DEFAULT '2' COMMENT '1-正式(formal);2-试用(trial);3-联调(test)',
 *   `total_minutes` int NOT NULL DEFAULT '0' COMMENT '质检分钟总数，充值后更新，范围1-100000',
 *   `used_minutes` int DEFAULT '0' COMMENT '已用质检分钟，定时任务扫描后更新',
 *   `validity` int DEFAULT NULL COMMENT '有效期,范围：0-180',
 *   `expire_time` datetime DEFAULT NULL COMMENT '失效时间',
 */

    private Integer id;

    private String name;

    private String nickname;

    private String fullname;

    private Integer parentId;

    private Integer status;

    private Integer ability;

    private String roleIdList;

    private String creator;

    private String comment;

    private Date createTime;;

    private Date updateTime;

    private Integer abilityStatus;

    private String appId;

    private String platformAppId;

    private Integer priority;

    private String url;

    private Integer isReuseAsr;

    private Integer source;

    private Integer customerType;

    private Integer totalMinutes;

    private Integer usedMinutes;

    private Integer validity;

    private Date expireTime;

    private String accountType;

    private String resultPushUrl;

    private Integer billMode;

    private String scenarioId;

    private Integer scriptId;


}
