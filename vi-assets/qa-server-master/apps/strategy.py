import random


# 通话时长分段质检策略返回值（resolve_segment_route）
SEGMENT_ROUTE_REALTIME = "realtime"
SEGMENT_ROUTE_NO_AI = "no_ai"
SEGMENT_ROUTE_NEAR_REALTIME = "near_realtime"
SEGMENT_ROUTE_OFFLINE = "offline"


def _segment_numeric(val, default=0.0):
    if val is None:
        return default
    try:
        return float(val)
    except (TypeError, ValueError):
        return default


def _sampling_ratio_fraction(val):
    """采样比例取值 0~100，转为 [0, 1] 区间概率；越界按边界截断。"""
    x = _segment_numeric(val, 0.0)
    x = max(0.0, min(100.0, x))
    return x / 100.0


def resolve_segment_route(duration, is_stream, app_info, rand_fn=None):
    """
    根据 userData 通话时长 duration 与 app_info 的分段配置决定质检路径。
    T1/T2 对应 call_duration_t1、call_duration_t2；P/P1/P2 对应 sampling_ratio_p、sampling_ratio_p1、sampling_ratio_p2（0~100）；
    is_segment：0 离线策略，1 实时策略。

    :param duration: 通话时长（与 T1、T2 同一时间单位，例如秒）
    :param is_stream: 是否实时质检，True 表示是，False 表示否
    :param app_info: 企业/客户配置（含 is_segment、call_duration_*、sampling_ratio_*）
    :param rand_fn: 可选，无参可调用，返回 [0,1) 内随机数，便于单测；默认 random.random
    :return: SEGMENT_ROUTE_REALTIME | SEGMENT_ROUTE_NO_AI | SEGMENT_ROUTE_NEAR_REALTIME | SEGMENT_ROUTE_OFFLINE
    """
    if is_stream:
        return SEGMENT_ROUTE_REALTIME
    rand_fn = rand_fn or random.random
    d = max(0.0, _segment_numeric(duration, 0.0))
    is_segment = app_info.get("is_segment", 0)
    try:
        is_segment = int(is_segment)
    except (TypeError, ValueError):
        is_segment = 0

    t1 = max(0.0, _segment_numeric(app_info.get("call_duration_t1"), 0.0))
    t2 = max(0.0, _segment_numeric(app_info.get("call_duration_t2"), 0.0))

    half_t1 = 0.5 * t1

    def in_short_tail_band():
        """时长落在 [0.5*T1, T1]（闭区间）；T1==0 时仅 d==0 视为落入。"""
        if t1 <= 0:
            return d == 0
        return half_t1 <= d <= t1

    if is_segment == 1:
        if d <= t1:
            if not in_short_tail_band():
                return SEGMENT_ROUTE_NO_AI
            p1 = _sampling_ratio_fraction(app_info.get("sampling_ratio_p1"))
            p2 = _sampling_ratio_fraction(app_info.get("sampling_ratio_p2"))
            s = p1 + p2
            if s > 1.0:
                p1, p2 = p1 / s, p2 / s
            r = rand_fn()
            if r < p1:
                return SEGMENT_ROUTE_NEAR_REALTIME
            if r < p1 + p2:
                return SEGMENT_ROUTE_OFFLINE
            return SEGMENT_ROUTE_NO_AI
        if d <= t2:
            return SEGMENT_ROUTE_NEAR_REALTIME
        return SEGMENT_ROUTE_REALTIME

    # is_segment == 0，离线
    if d <= t1:
        if not in_short_tail_band():
            return SEGMENT_ROUTE_NO_AI
        p = _sampling_ratio_fraction(app_info.get("sampling_ratio_p"))
        r = rand_fn()
        if r < p:
            return SEGMENT_ROUTE_OFFLINE
        return SEGMENT_ROUTE_NO_AI
    return SEGMENT_ROUTE_OFFLINE


def resolve_segment_route_from_user_data(user_data, app_info, rand_fn=None):
    """
    从 userData 读取 duration，交给 resolve_segment_route。
    user_data 可为 None 或不含 duration 时，时长按 0 处理。
    """
    duration = (user_data or {}).get("duration", 0)
    is_stream = (user_data or {}).get("isStream", True)
    return resolve_segment_route(duration, is_stream, app_info, rand_fn=rand_fn)
