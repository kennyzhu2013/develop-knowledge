package com.cmic.pojo.entity;

import com.cmic.util.JacksonUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sub_task")
@CompoundIndexes({
        @CompoundIndex(name = "taskId_status_idx", def = "{'taskId':1, 'status':1}"),
        @CompoundIndex(name = "taskId_callback_index", def = "{'taskId': 1, 'callback': 1}")
})
public class SubTask implements Serializable {
    @Id
    private long id;

    private long taskId;

    private int status = 0; //子任务推送 MQ 状态 0-未开始 1-推送失败 2-推送成功

    private int finish = 0; //0-失败，1-成功，2-部分成功

    public int location = 1; //位置：1 南，2 北; 默认南方

    private String errorMessage;

    private String dataId;

    /**
     * text subTask: text
     * audio subTask: url
     * 声纹: dataId, urlList:url
     */
    private Map<String, Object> attributes;

    @JsonIgnore
//    @CreatedDate
    private LocalDateTime createTime;

    @JsonIgnore
//    @LastModifiedDate
    private LocalDateTime updateTime;

    private int callback = 0;   //0-未回调，1-已回调

    private Map<String, Object> responseResults;

    public Map<String, Object> getResponseResults() {
        if(CollectionUtils.isEmpty(responseResults)){
            responseResults = new HashMap<>();
        }
        return responseResults;
    }

    @SuppressWarnings("unchecked")
    public Object getModelInfoResult(String... paths) {
        if (responseResults == null || paths == null || paths.length == 0) {
            return null;
        }

        Object current = responseResults;
        for (String path : paths) {
            path = path.toLowerCase();
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<String, Object>) current).get(path);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    public String getErrorMessage(String newAttribute, Object newValue){
        try {
            String json;
            if(StringUtils.hasLength(errorMessage)){
                json = JacksonUtil.addJsonAttribute(errorMessage, newAttribute, newValue);
            }else{
                json = JacksonUtil.createJson(newAttribute, newValue);
            }
            errorMessage = json;
            return json;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {

        return "SubTask{" +
                "id=" + id +
                ", taskId=" + taskId +
                ", status=" + status +
                ", finish=" + finish +
                ", location=" + location +
                ", callback=" + callback +
                ", dataId='" + dataId + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                ", attributes=" + attributes +
                ", responseResults=" + responseResults +
                '}';
    }
}
