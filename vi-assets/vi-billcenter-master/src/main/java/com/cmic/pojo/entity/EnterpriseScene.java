package com.cmic.pojo.entity;

import lombok.Data;

import java.util.Date;

@Data
public class EnterpriseScene {

    private Integer id;

    private Date createTime;;

    private Date updateTime;

    private String code;

    private String name;

    private String wordPackageId;

}
