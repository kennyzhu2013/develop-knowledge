package com.cmic.pojo.entity;

import lombok.Data;

import java.util.Date;


@Data
public class CompanyTab {
    /**
     * `id` int NOT NULL AUTO_INCREMENT COMMENT '主键id',
     * `name` varchar(100) NOT NULL COMMENT '企业英文名（可用拼音代替）',
     * `nickname` varchar(100) NOT NULL DEFAULT '' COMMENT '企业中文简称',
     * `fullname` varchar(100) NOT NULL COMMENT '企业中文全称',
     * `app_key` varchar(50) NOT NULL COMMENT '企业鉴权id',
     * `app_secret` varchar(100) NOT NULL COMMENT '企业鉴权秘钥',
     * `parent_id` int NOT NULL COMMENT '父级企业ID',
     * `status` tinyint(1) NOT NULL DEFAULT '3' COMMENT '状态，枚举值：1-启用、2-停用、3-注销',
     * `role_id_list` varchar(100) DEFAULT '' COMMENT '关联角色id列表，以逗号分隔',
     * `creator` varchar(50) NOT NULL DEFAULT '' COMMENT '创建者',
     * `comment` varchar(100) DEFAULT '' COMMENT '备注',
     * `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
     * `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
     */
    private Integer id;
    private String name;
    private String nickname;
    private String fullname;
    private String appKey;
    private String appSecret;
    private Integer parentId;
    private Integer status;
    private String roleIdList;
    private String creator;
    private String comment;
    private Date createTime;
    private Date updateTime;
}
