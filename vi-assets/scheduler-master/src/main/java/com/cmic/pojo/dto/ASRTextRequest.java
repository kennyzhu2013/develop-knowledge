package com.cmic.pojo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ASRTextRequest extends BaseRequest{
    @Valid
    @NotEmpty(message = "asrTexts should not be empty")
    @Size(max=100000, message = "max length of asrTexts is 100000")
    private List<ASRText> asrTexts;

    @Data
    public static class ASRText{
        private String id;
        private String text;
        public String location = "1";
    }
}
