package com.cmic.pojo.entity;

import lombok.Data;

import java.util.Date;

@Data
public class Bill {

    private String id;

    private int mobileId;

    private int channelId;

    private int companyId;

    private int customerId;

    private String appId;

    private int accountType;

    private int location;

    private int asId;

    private int msId;

    private int pfsId;

    private String originalCallerNumber;

    private String callerNumber;

    private String originalCalleeNumber;

    private String calleeNumber;

    private String bizNumber;

    private int callInProvinceId;

    private int callOutProvinceId;

    private String callIn700AreaCode;

    private Date handleStartTime;

    private Date handleEndTime;

    private Date callForwardTime;

    private Date ringTime;

    private Date callStartTime;

    private Date callEndTime;

    private int callDurationS;

    private int callDurationM;

    private int fileDuration;

    private int leftFileLength;

    private int rightFileLength;

    private Date firstRespTime;

    private int q850;

    private int mediaLDirection;

    private int mediaRDirection;

    public int callState;

    private int finishType;

    private int finishState;

    private int appendReason;

    private int isBillOk;

    private int isRecordOk;

    private int recordType;

    private Integer qualityInspectionStatus;

    private String callOutAreaCode;

    private String callInAreaCode;
}
