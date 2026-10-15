package com.cmic.recording.mediaevent;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * @author XH
 * @description: 媒体回调
 * @date 2025/8/1 10:47
 */
@Data
@SuperBuilder
@NoArgsConstructor
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "event",
        visible = true,
        defaultImpl = UnknownEvent.class
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = DtmfDoneEvent.class, name = "DTMF_DONE"),
        @JsonSubTypes.Type(value = AudioDoneEvent.class, name= "AUDIO_DONE"),
        @JsonSubTypes.Type(value = VideoDoneEvent.class, name= "VIDEO_DONE"),
        @JsonSubTypes.Type(value = EnhanceDoneEvent.class, name= "ENHANCE_DONE"),
})
public class CallBackEvent<T> {
    String sessionId;
    String event;
    String code;
    String message;
    int seqId;
    T data;
}
