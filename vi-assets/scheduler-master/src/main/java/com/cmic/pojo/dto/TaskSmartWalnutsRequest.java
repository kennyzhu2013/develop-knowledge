package com.cmic.pojo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class TaskSmartWalnutsRequest extends BaseRequest{

    /**
     * 待质检录音下载链接列表，每个File对象为一个id对应一个录音下载url
     */
    @Valid
    @NotEmpty(message = "urlList should not be empty")
    @Size(max=100000, message = "max length of urlList is 100000")
    public List<RequestFile> urlList;

}