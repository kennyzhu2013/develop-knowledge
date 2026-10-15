package com.cmic.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

@Data
public class RequestFile {
    /**
     * 唯一标识文件的id
     */
    @NotBlank(message = "file id cannot be empty")
    public String id;

    /**
     * 文件下载url
     */
    @NotBlank(message = "file url cannot be empty")
    @URL(message = "file url is illegal")
    public String url;

    /**
     * 集群编号
     * 1-南方集群；2-北方集群
     */
    @Pattern(regexp = "[12]", message = "illegal location")
    public String location = "1";
}
