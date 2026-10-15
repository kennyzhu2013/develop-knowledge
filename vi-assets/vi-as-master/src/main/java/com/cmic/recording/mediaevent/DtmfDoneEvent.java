package com.cmic.recording.mediaevent;

import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * @author XH
 * @description:
 * @date 2025/8/1 10:48
 */
@SuperBuilder
@AllArgsConstructor
public class DtmfDoneEvent extends CallBackEvent<DtmfData> {
}
