package com.cmic.recording.mediaevent;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 收号回调事件内容
 *
 * @author XH
 * @version 1.0
 * @date 2025-08-28
 */
@Data
@NoArgsConstructor
public class DtmfData {
    String digits;
    String endCause;
    int source;
}
