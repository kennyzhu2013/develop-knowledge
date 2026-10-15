from datetime import datetime

from clickhouse_sqlalchemy import types, engines
from sqlalchemy import String, Column, DateTime, text, SmallInteger, Integer, Float, MetaData, Text, BigInteger, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from utils.clickhouse import CkBase
from utils.mysql import TableMixin, MysqlBase

dcc_qa_detail = MetaData()
mid_qa_detail = MetaData()
mid_qa_segment = MetaData()


class QaDetailBase(object):

    id = Column(types.Int64, primary_key=True, nullable=False, comment="记录ID")
    create_time = Column(types.DateTime, nullable=False, comment="创建时间")
    finish_time = Column(types.DateTime, nullable=False, comment="完成时间")

    app_id = Column(types.String, server_default="", nullable=False, comment="渠道id")
    qa_mobile_id = Column(types.Int32, comment="质检账户-一级移动ID")
    qa_channel_id = Column(types.Int32, comment="质检账户-二级移动ID")
    qa_company_id = Column(types.Int32, comment="质检账户-接入企业ID")
    qa_customer_id = Column(types.Int32, comment="质检账户-终端客户ID")
    task_id = Column(types.String, server_default="", nullable=False, comment="任务id")
    data_id = Column(types.String, server_default="", nullable=False, comment="录音id")

    caller = Column(types.String, server_default="", nullable=False, comment="真实主叫")
    callee = Column(types.String, server_default="", nullable=False, comment="真实被叫")
    mid_num = Column(types.String, server_default="", nullable=False, comment="中间号")
    display_num = Column(types.String, server_default="", nullable=False, comment="号显")
    transfer_callee = Column(types.String, server_default="", nullable=False, comment="呼转被叫")
    call_time = Column(types.DateTime, comment="通话发生时间")

    strategy_type = Column(types.String, server_default="", comment="质检策略")
    status = Column(types.String, server_default="", nullable=False, comment="质检状态 1成功 0失败 3重试中")
    duration = Column(types.Int64, server_default=text("0"), nullable=False, comment="录音时长")
    duration_min = Column(types.Int64, server_default=text("0"), nullable=False, comment="录音时长,按分钟取整")
    duration_ps = Column(types.Int64, server_default=text("0"), nullable=False, comment="录音时长,按6秒取整")
    is_need_review = Column(types.String, server_default="", nullable=False, comment="是否需要复核 1是 0否")
    url = Column(types.String, server_default="", nullable=False, comment="录音下载地址")

    content = Column(types.String, server_default="", nullable=False, comment="ASR结果")
    asr_info = Column(types.String, server_default="", nullable=False, comment="ASR详细信息")
    matched_word = Column(types.String, server_default="", nullable=False, comment="匹配到的关键词")
    summary = Column(types.String, server_default="", nullable=False, comment="录音摘要结果")
    emotion_analysis = Column(types.String, server_default="", nullable=False, comment="情感分析结果")
    scene = Column(types.String, server_default="", nullable=False, comment="场景识别结果")
    script_consistency = Column(types.String, server_default="", nullable=False, comment="话术一致性结果")
    matched_script = Column(types.String, server_default="", nullable=False, comment="匹配到的话术ID")
    scene_consistency = Column(types.String, server_default="", nullable=False, comment="场景一致性结果")
    scene_score = Column(types.Float64, server_default="", nullable=False, comment="场景一致性分数")
    vfd_result = Column(types.String, server_default="", nullable=False, comment="声音鉴伪结果")
    vfd_score = Column(types.Float64, server_default="", nullable=False, comment="声音鉴伪分数")
    keyword_analysis_result = Column(types.String, server_default="", nullable=False, comment="关键词分析结果 1-涉诈 0-不涉诈")
    keyword_analysis_score = Column(types.String, server_default="", nullable=False, comment="关键词分析分数")
    keyword_analysis_threshold = Column(types.String, server_default="", nullable=False, comment="关键词分析阈值")
    keyword_analysis_words = Column(types.String, server_default="", nullable=False, comment="关键词分析命中关键词的分数")
    keyword_analysis_weights = Column(types.String, server_default="", nullable=False, comment="关键词分析命中关键词的权重")
    customized_detection_result = Column(types.String, server_default="", nullable=False, comment="dify检测结果")
    rt_result_code = Column(types.String, server_default="", nullable=False, comment="融通质检-反馈码")
    rt_ability_type = Column(types.String, server_default="", nullable=False, comment="融通质检-能力字段")
    rt_first_label = Column(types.String, server_default="", nullable=False, comment="融通质检-一级标签")
    rt_second_label = Column(types.String, server_default="", nullable=False, comment="融通质检-二级标签")
    rt_third_label = Column(types.Float64, server_default="", nullable=False, comment="融通质检-三级标签")
    rt_svc_type = Column(types.String, server_default="", nullable=False, comment="融通质检-业务类型")
    cmic_internal_collection_result = Column(types.String, server_default="", nullable=False, comment="催收模型分析结果")
    collection_score = Column(types.Float64, server_default="", nullable=False, comment="催收模型分析分数")
    process_id = Column(types.String, server_default="", nullable=False, comment="调度系统流程ID")
    model_status = Column(types.String, server_default="", nullable=False, comment="各模型执行情况")
    data = Column(types.String, server_default="", nullable=False, comment="透传数据")
    error_message = Column(types.String, server_default="", nullable=False, comment="错误信息")
    content_similarity_qc_result = Column(types.String, server_default="", nullable=False, comment="文本相似度检测结果")
    content_similarity_score = Column(types.Float64, server_default="", nullable=False, comment="文本相似度检测分数")
    text = Column(types.String, server_default="", nullable=False, comment="文本质检任务内容")
    remark1 = Column(types.String, server_default="", nullable=False, comment="客户侧信息")
    remark2 = Column(types.String, server_default="", nullable=False, comment="客户侧信息")
    remark3 = Column(types.String, server_default="", nullable=False, comment="客户侧信息")
    remark4 = Column(types.String, server_default="", nullable=False, comment="客户侧信息")
    porn_related = Column(types.String, server_default="", nullable=False, comment="是否涉黄，1-是，0-否")
    politics_related = Column(types.String, server_default="", nullable=False, comment="是否涉政，1-是，0-否")
    violence_related = Column(types.String, server_default="", nullable=False, comment="是否涉暴，1-是，0-否")
    gambledrug_related = Column(types.String, server_default="", nullable=False, comment="是否涉赌毒，1-是，0-否")
    audio_tag = Column(types.String, server_default="", nullable=False, comment="声纹标签")
    vp_score = Column(types.String, server_default="", nullable=False, comment="声纹1:1比对分数")
    vp_top_k_tags = Column(types.String, server_default="", nullable=False, comment="声纹1:N比对结果")
    identity_consistency = Column(types.String, server_default="", nullable=False, comment="从业者身份比对结果，1-是，0-否")
    identity_score = Column(types.Float64, server_default="", nullable=False, comment="从业者身份比对分数")
    deepseek_result = Column(types.String, server_default="", nullable=False, comment="结果状态 1-是 0-否")
    deepseek_reason = Column(types.String, server_default="", nullable=False, comment="原因分析")
    deepseek_extra_data = Column(types.String, server_default="", nullable=False, comment="额外字段")
    aigs_content = Column(types.String, server_default="", nullable=False, comment="通话内容")
    aigs_reason = Column(types.String, server_default="", nullable=False, comment="原因分析")
    aigs_confidence = Column(types.String, server_default="", nullable=False, comment="置信度")
    aigs_is_fraud = Column(types.String, server_default="", nullable=False, comment="是否涉诈 0-否 1-是")
    word_group_result = Column(types.String, server_default="", nullable=False, comment="是否命中词群 0-未命中 1-命中")
    emotion_result = Column(types.String, server_default="", nullable=False, comment="情绪识别结果")
    qwen_result = Column(types.String, server_default="", nullable=False, comment="千问模型结果")
    rt_review_result_code = Column(types.String, server_default="", nullable=False, comment="融通涉诈复核-反馈码")
    rt_review_ability_type = Column(types.String, server_default="", nullable=False, comment="融通涉诈复核-能力字段")
    rt_review_first_label = Column(types.String, server_default="", nullable=False, comment="融通涉诈复核-一级标签")
    rt_review_second_label = Column(types.String, server_default="", nullable=False, comment="融通涉诈复核-二级标签")
    rt_review_third_label = Column(types.Float64, server_default="", nullable=False, comment="融通涉诈复核-三级标签")
    market_call_result = Column(types.String, server_default="", nullable=False, comment="营销外呼模型结果")
    regex_result = Column(types.String, server_default="", nullable=False, comment="正则匹配结果")
    deal_label = Column(types.String, server_default="", nullable=False, comment="分类结果标签 撮合成交/指定成交")
    deal_need_review = Column(types.String, server_default="", nullable=False, comment="是否需要人工复核 1-需要 0-不需要")
    rag_results = Column(types.String, server_default="", nullable=False, comment="rag检索结果数组")
    rag_count = Column(types.Int64, server_default="", nullable=False, comment="rag检索返回结果数量")
    keyword_analysis_lt_result = Column(types.String, server_default="", nullable=False, comment="关键词反诈结果（低阈值）")
    qc_result = Column(types.String, server_default="", nullable=False, comment="质检结果 0-异常 1-正常")
    risk_level = Column(types.String, server_default="", nullable=False, comment="质检结果风险等级")
    model_info = Column(types.String, server_default="", nullable=False, comment="各模型执行情况")
    qa_type = Column(types.Int8, server_default="", nullable=False, comment="质检类型 1-普通质检 2-专线质检")
    user_data = Column(types.String, server_default="", nullable=False, comment="专线质检透传数据")
    ctrl_action = Column(types.String, server_default="", nullable=False, comment="专线质检管控指令")
    audio_code = Column(types.String, server_default="", nullable=False, comment="专线质检放音编码")
    audio_direction = Column(types.String, server_default="", nullable=False, comment="专线质检放音对象")
    control_type = Column(types.String, server_default="", nullable=False, comment="专线质检管控类型")
    bge_rule_name = Column(types.String, server_default="", nullable=False, comment="BGE命中规则名称")
    bge_expression = Column(types.String, server_default="", nullable=False, comment="BGE命中规则表达式")
    bge_output_label = Column(types.String, server_default="", nullable=False, comment="BGE输出label")
    bge_priority = Column(types.String, server_default="", nullable=False, comment="BGE规则优先级")

    cq_anti_filter_type = Column(types.String, server_default="", nullable=False, comment="重庆行业卡反诈-一级标签类型 0-正常 3-涉诈")
    cq_anti_risk_level = Column(types.String, server_default="", nullable=False, comment="重庆行业卡反诈-风险等级 0-正常 1-低 2-中 3-高")
    cq_anti_result = Column(types.String, server_default="", nullable=False, comment="重庆行业卡反诈-二级标签识别结果列表")

    cq_agent_is_violation = Column(types.Boolean, server_default="0", comment="是否违规，默认 false")
    cq_agent_is_fraud = Column(types.Boolean, server_default="0", comment="是否涉诈，默认 false")
    cq_agent_risk_level = Column(types.String, server_default="", nullable=False, comment="风险等级：合规 / 低风险 / 中风险 / 高风险")
    cq_agent_scene_category = Column(types.String, server_default="", nullable=False, comment="违规场景大类，如 引流第三方平台；正常时为 正常")
    cq_agent_scene_subtype = Column(types.String, server_default="", nullable=False, comment="违规场景子类，如 引导用户主动添加微信")
    cq_agent_confidence = Column(types.Float64, comment="置信度（0.0 ~ 1.0）")
    cq_agent_source = Column(types.String, server_default="", nullable=False, comment="结果来源：llm（模型循环思考输出）llm-fast（模型快速输出）或 heuristic（启发式兜底）")
    cq_agent_detail = Column(types.String, server_default="", nullable=False, comment="详情")

    tag = Column(types.String, server_default="", nullable=False, comment="标签")

    is_segment = Column(types.Int64, comment="是否实时分片")
    segment_id = Column(types.Int64, comment="分片ID")
    is_stream = Column(types.Int64, comment="是否实时流")
    end_flag = Column(types.Int64, comment="是否结束片 0-否 1-是")
    rule_id = Column(types.Int64, comment="质检规则ID")

    def to_dict(self):
        return {c.name: getattr(self, c.name) for c in self.__table__.columns}


class QaRuleBase(object):
    id = Column(Integer, primary_key=True, autoincrement=True)
    create_time = Column(DateTime, server_default=text("CURRENT_TIMESTAMP"), nullable=False)
    update_time = Column(DateTime, server_default=text("CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"), nullable=False)

    app_id = Column(String(10), server_default="", nullable=False, comment="质检账户id")
    name = Column(String(255), server_default="", nullable=False, comment="规则名称")
    qa_mobile_id = Column(Integer, comment="质检账户-一级移动ID")
    qa_channel_id = Column(Integer, comment="质检账户-二级移动ID")
    qa_company_id = Column(Integer, comment="质检账户-接入企业ID")
    qa_customer_id = Column(Integer, comment="质检账户-终端客户ID")

    process_id = Column(String(100), server_default="", nullable=False, comment="流程ID")
    comment = Column(String(255), server_default="", nullable=False, comment="规则说明")
    priority = Column(String(10), server_default="", nullable=False, comment="优先级 0-10")
    strategy_type = Column(String(10), default="", nullable=False, comment="质检策略")

    status = Column(String(1), server_default="0", nullable=False, comment="状态 1-正常 2-暂停 3-取消")
    is_periodic = Column(String(1), server_default="0", nullable=False, comment="是否周期性任务 0-否 1-是")
    first_execute_time = Column(DateTime, nullable=False, comment="首次执行时间")
    next_execute_time = Column(DateTime, nullable=False, comment="下次执行时间")
    period = Column(String(255), server_default="", nullable=False, comment="周期，格式：数字+字母，如1h")

    is_asr = Column(String(1), server_default="0", nullable=False,
                    comment="是否需要进行语音转写结果（默认0）0-不需要 1-需要")
    asr_type = Column(String(1), server_default="", nullable=False, comment="语音转写声道 0双声道 1左声道 2右声道")
    asr_language = Column(String(10), server_default="", nullable=False, comment="ASR语言参数")
    asr_emotion_tag = Column(String(20), server_default="", nullable=False, comment="ASR情绪识别标签")
    is_word_match = Column(String(1), server_default="0", nullable=False,
                           comment="是否需要进行关键词对比（默认0）0-不需要 1-需要")
    word_package_id = Column(String(255), server_default="0", nullable=False,
                             comment="关键词对比使用的词袋id，最多支持5个id，使用英文逗号隔开")
    is_summarize = Column(String(1), server_default="0", nullable=False,
                          comment="是否需要进行文本总结（默认0）0-不需要 1-需要")
    is_emotion_analyse = Column(String(1), server_default="0", nullable=False,
                                comment="是否需要进行情感分析（默认0）0-不需要 1-需要")
    emotion_channel = Column(SmallInteger, server_default=text("0"), nullable=False, comment="情绪识别处理声道")
    is_scene_compare = Column(String(1), server_default="0", nullable=False,
                              comment="是否需要进行场景一致性对比（默认0）0-不需要 1-需要")
    expected_scene = Column(String(255), server_default="", nullable=False, comment="预期场景，场景一致性对比时不为空")
    is_script_compare = Column(String(1), server_default="0", nullable=False,
                               comment="是否需要进行话术一致性对比（默认0）0-不需要 1-需要")
    script_id = Column(String(255), server_default="", nullable=False,
                       comment="话术文本id，使用英文逗号隔开")

    is_dify = Column(String(1), server_default="0", nullable=False,
                     comment="是否进行dify模型分类（默认0）0-不需要 1-需要")
    is_vfd = Column(String(1), server_default="0", nullable=False, comment="是否进行声音鉴伪（默认0）0-不需要 1-需要")
    is_scene_recognize = Column(String(1), server_default="0", nullable=False,
                                comment="是否进行场景识别（默认0）0-不需要 1-需要")
    is_keyword_analyse = Column(String(1), server_default="0", nullable=False,
                                comment="是否进行关键词分析（默认0）0-不需要 1-需要")
    fd_scene = Column(String(100), server_default="0", nullable=False,
                      comment="关键词分析预期场景,is_keyword_analyse=1时必填")

    is_voice_analyse = Column(String(1), server_default="0", nullable=False,
                              comment="是否进行声纹识别（默认0）0-不需要 1-需要")
    is_voice_compare = Column(String(1), server_default="0", nullable=False,
                              comment="是否进行声纹比对（默认0）0-不需要 1-需要")
    vp_db = Column(String(100), server_default="", nullable=False, comment="声纹库名")
    vp_channel = Column(String(10), server_default="", nullable=False, comment="声纹-指定合音左右腿（0/1）")
    vp_topn = Column(String(10), server_default="", nullable=False,
                     comment="声纹1:N-指定返回N个最匹配的特征结果，结果包括比对分数和特征名")

    is_rt_check = Column(String(1), server_default="0", nullable=False,
                         comment="是否进行融通质检（默认0）0-不需要 1-需要")
    rt_svc_type = Column(String(10), server_default="", nullable=False, comment="融通业务类型编号 0-涉诈 1-场景一致性")
    rt_expected_scene = Column(String(10), server_default="", nullable=False, comment="预期场景,rt_svc_type=1时必填")

    is_similarity_analyse = Column(String(1), server_default="0", nullable=False,
                                   comment="是否检测文本相似度（默认0）0-不需要 1-需要")
    text_length = Column(String(10), server_default="", nullable=False, comment="需要质检的字符长度")
    head_tail = Column(String(10), server_default="", nullable=False,
                       comment="文本相似度检测取头部还是尾部 0-头部 1-尾部")
    text_threshold = Column(String(10), server_default="", nullable=False, comment="文本相似度判断阈值")

    is_collection_analyse = Column(String(1), server_default="0", nullable=False,
                                   comment="是否催收模型分析（默认0）0-不需要 1-需要")

    is_porn_analyse = Column(String(1), server_default="0", nullable=False,
                             comment="是否涉黄分析（默认0）0-不需要 1-需要")
    is_politics_analyse = Column(String(1), server_default="0", nullable=False,
                                 comment="是否涉政分析（默认0）0-不需要 1-需要")
    is_violence_analyse = Column(String(1), server_default="0", nullable=False,
                                 comment="是否涉暴分析（默认0）0-不需要 1-需要")
    is_gambledrug_analyse = Column(String(1), server_default="0", nullable=False,
                                   comment="是否涉赌毒分析（默认0）0-不需要 1-需要")

    is_identity_compare = Column(String(1), server_default="0", nullable=False,
                                 comment="是否身份比对（默认0）0-不需要 1-需要")
    identity_preset = Column(String(10), server_default="", nullable=False, comment="身份比对-预设值")

    is_aigs_analyse = Column(String(1), server_default="0", nullable=False,
                             comment="是否aigs分析（默认0）0-不需要 1-需要")

    is_deepseek_analyse = Column(String(1), server_default="0", nullable=False,
                                 comment="是否deepseek分析（默认0）0-不需要 1-需要")
    deepseek_prompt = Column(Text, comment="deepseek提示词")
    deepseek_prompt_id = Column(String(10), server_default="", nullable=False, comment="deepseek提示词Id")

    is_word_analyse = Column(String(1), server_default="0", nullable=False,
                             comment="是否词群匹配分析（默认0）0-不需要 1-需要")
    word_group_id = Column(String(255), server_default="", nullable=False, comment="词群id，使用英文逗号隔开")

    is_qwen_analyse = Column(String(1), server_default="0", nullable=False,
                             comment="是否qwen分析（默认0）0-不需要 1-需要")
    qwen_prompt = Column(Text, comment="qwen提示词")
    qwen_prompt_id = Column(String(10), server_default="", nullable=False, comment="qwen提示词Id")
    is_rt_fraud_review = Column(String(1), server_default="0", nullable=False,
                                comment="是否融通涉诈复核（默认0）0-不需要 1-需要")
    is_market_call_analyse = Column(String(1), server_default="0", nullable=False,
                                    comment="是否营销外呼分析（默认0）0-不需要 1-需要")
    is_regex_analyse = Column(String(1), server_default="0", nullable=False,
                             comment="是否正则匹配（默认0）0-不需要 1-需要")
    regex_id = Column(String(255), server_default="", nullable=False, comment="正则规则id，使用英文逗号隔开")
    is_deal_class = Column(String(1), server_default="0", nullable=False, comment="是否成交分类（默认0）0-不需要 1-需要")

    is_bge_classifier = Column(String(1), server_default="0", nullable=False,
                              comment="是否BGE分类（默认0）0-不需要 1-需要")
    bge_classifiers = Column(String(255), server_default="", nullable=False,
                            comment="BGE模型名称，多个用英文逗号隔开")
    bge_rule_id = Column(String(10), server_default="", nullable=False, comment="BGE主规则ID")

    rag_top_k = Column(Integer, comment="返回检索最相似结果的数量")
    is_rag_search = Column(String(1), server_default="0", nullable=False, comment="是否rag检索（默认0）0-不需要 1-需要")

    is_keyword_analyse_lt = Column(String(1), server_default="0", nullable=False, comment="是否进行关键词分析（低阈值）（默认0）0-不需要 1-需要")
    is_cq_fraud_check = Column(String(1), server_default="0", nullable=False, comment="是否进行重庆行业卡反诈（默认0）0-不需要 1-需要")

    is_cq_agent_review = Column(String(1), server_default="0", nullable=False, comment="是否进行重庆行业卡Agent复核（默认0）0-不需要 1-需要")
    cq_agent_use_tools = Column(SmallInteger, comment="使用完整 agentic tool loop（更慢更准），1-是 0-否")
    cq_agent_use_fast = Column(SmallInteger, comment="使用快速检索增强单轮模式，1-是 0-否")

    creator = Column(String(100), server_default="", nullable=False, comment="创建人")
    modifier = Column(String(100), server_default="", nullable=False, comment="修改人")

    main_url = Column(String(255), server_default="", nullable=False, comment="质检总结果回调地址")
    sub_url = Column(String(255), server_default="", nullable=False, comment="质检子结果回调地址")
    data = Column(String(255), server_default="", nullable=False, comment="透传数据")

    def to_dict(self):
        return {c.name: getattr(self, c.name) for c in self.__table__.columns if not c.name.endswith("time")}

    def to_task_params(self):
        param = {"transcription": self.is_asr, "transcriptionType": self.asr_type,
                "wordMatching": self.is_word_match, "wordPackageId": self.word_package_id,
                "summary": self.is_summarize, "emotionAnalysis": self.is_emotion_analyse,
                "sceneAnalysis": self.is_scene_compare, "sceneConsistency": self.expected_scene,
                "scriptConsistency": self.is_script_compare, "scriptId": self.script_id,
                "forgeryDetection": self.is_vfd, "sceneRecognition": self.is_scene_recognize,
                "fraudDetection": self.is_keyword_analyse, "fusionFraudSceneCheck": self.is_rt_check,
                "rtType": self.rt_svc_type, "rtScene": self.rt_expected_scene,
                "taskCallBackUrl": self.main_url, "subTaskCallBackUrl": self.sub_url, "data": self.data,
                "channelId": self.app_id, "strategyType": self.strategy_type,
                "ruleId": self.id, "fraudDetectionScene": self.fd_scene,
                "identityComparison": self.is_identity_compare, "identityPreset": self.identity_preset,
                "voiceComparison": self.is_voice_compare, "voiceAnalysis": self.is_voice_analyse,
                "voicePrintDb": self.vp_db, "vpChannel": self.vp_channel, "vpTopn": self.vp_topn,
                "deepSeekAnalysis": self.is_deepseek_analyse, "deepSeekPromptId": self.deepseek_prompt_id,
                "aigsAnalysis": self.is_aigs_analyse, "pornAnalysis": self.is_porn_analyse,
                "politicsAnalysis": self.is_politics_analyse, "violenceAnalysis": self.is_violence_analyse,
                "gambleDrugAnalysis": self.is_gambledrug_analyse, "priority": self.priority,
                "asrEmotionTag": self.asr_emotion_tag, "asrLanguage": self.asr_language,
                "wordAnalysis": self.is_word_analyse, "wordGroupId": self.word_group_id,
                "emotionChannel": self.emotion_channel, "regexWordMatching": self.is_regex_analyse,
                "regexId": self.regex_id, "qwenInference": self.is_qwen_analyse, "qwenPromptId": self.qwen_prompt_id,
                "dealClass": self.is_deal_class, "ragSearch": self.is_rag_search, "ragTopK": self.rag_top_k,
                "fraudDetectionLT": self.is_keyword_analyse_lt,
                "bgeClassifier": self.is_bge_classifier, "classifiers": self.bge_classifiers,
                "bgeRuleId": self.bge_rule_id, "cqFraudCheck": self.is_cq_fraud_check,
                "cqAgentReview": self.is_cq_agent_review,
                "cqAgentUseTools": self.cq_agent_use_tools,
                "cqAgentUseFast": self.cq_agent_use_fast}

        return param


class QaTaskBase(object):
    id = Column(String(255), nullable=False, primary_key=True, comment="任务id")
    create_time = Column(DateTime, server_default=text("CURRENT_TIMESTAMP"), nullable=False)
    update_time = Column(DateTime, server_default=text("CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"), nullable=False)

    rule_id = Column(Integer, server_default=text("0"), nullable=False, comment="对应规则id")
    status = Column(String(1), server_default="0", nullable=False, comment="质检状态 1已完成 0进行中")
    completion_ratio = Column(Integer, comment="完成比例")
    total = Column(Integer, nullable=False, comment="子任务总数")
    error_num = Column(Integer, nullable=False, comment="子任务失败数量")
    avg_duration = Column(Integer, server_default=text("0"), nullable=False, comment="近七天日均通话分钟数")
    # ASR
    asr_num = Column(Integer, server_default=text("0"), nullable=False, comment="ASR数量")
    fraud_reviewed_num = Column(Integer, server_default=text("0"), nullable=False, comment="人工复核涉诈条数")
    # 关键词匹配
    word_num = Column(Integer, server_default=text("0"), nullable=False, comment="关键词质检条数")
    matched_word_num = Column(Integer, server_default=text("0"), nullable=False, comment="命中关键词条数")
    word_reviewed_num = Column(Integer, server_default=text("0"), nullable=False, comment="人工复核关键词条数")
    matched_word_reviewed_num = Column(Integer, server_default=text("0"), nullable=False, comment="人工复核关键词命中条数")
    # 情绪分析
    emotion_analysis_num = Column(Integer, server_default=text("0"), nullable=False, comment="情绪分析条数")
    bad_emotion_num = Column(Integer, server_default=text("0"), nullable=False, comment="情绪异常条数")
    emotion_reviewed_num = Column(Integer, server_default=text("0"), nullable=False, comment="人工复核情绪质检条数")
    bad_emotion_reviewed_num = Column(Integer, server_default=text("0"), nullable=False, comment="人工复核情绪异常条数")
    # 场景识别
    scene_num = Column(Integer, server_default=text("0"), nullable=False, comment="场景识别条数")
    scene_reviewed_num = Column(Integer, server_default=text("0"), nullable=False, comment="人工复核场景识别条数")
    right_scene_reviewed_num = Column(Integer, server_default=text("0"), nullable=False, comment="人工复核场景识别正确条数")
    # 场景一致性
    scene_consistency_num = Column(Integer, server_default=text("0"), nullable=False, comment="场景一致性质检条数")
    scene_inconsistent_num = Column(Integer, server_default=text("0"), nullable=False, comment="场景不一致条数")
    scene_consistency_reviewed_num = Column(Integer, server_default=text("0"), nullable=False, comment="人工复核场景一致性条数")
    scene_inconsistent_reviewed_num = Column(Integer, server_default=text("0"), nullable=False, comment="人工复核场景不一致条数")
    # 话术一致性
    script_analysis_num = Column(Integer, server_default=text("0"), nullable=False, comment="话术一致性质检条数")
    script_inconsistent_num = Column(Integer, server_default=text("0"), nullable=False, comment="话术不一致条数")
    script_analysis_reviewed_num = Column(Integer, server_default=text("0"), nullable=False, comment="人工复核话术一致性条数")
    script_inconsistent_comment_num = Column(Integer, server_default=text("0"), nullable=False, comment="人工复核话术不一致条数")
    # 声音鉴伪
    vfd_num = Column(Integer, server_default=text("0"), nullable=False, comment="声音鉴伪条数")
    fake_vfd_num = Column(Integer, server_default=text("0"), nullable=False, comment="伪造音频条数")
    vfd_reviewed_num = Column(Integer, server_default=text("0"), nullable=False, comment="人工复核声音鉴伪条数")
    fake_vfd_reviewed_num = Column(Integer, server_default=text("0"), nullable=False, comment="人工复核伪造音频条数")
    # 声纹分析
    voice_analysis_num = Column(Integer, server_default=text("0"), nullable=False, comment="声纹识别条数")
    abnormal_voice_num = Column(Integer, server_default=text("0"), nullable=False, comment="声纹识别异常条数")
    voice_analysis_reviewed_num = Column(Integer, server_default=text("0"), nullable=False, comment="人工复核声纹识别条数")
    abnormal_voice_reviewed_num = Column(Integer, server_default=text("0"), nullable=False, comment="人工复核异常声纹条数")
    # 声纹比对
    voice_compare_num = Column(Integer, server_default=text("0"), nullable=False, comment="声纹比对组数")
    voice_inconsistent_num = Column(Integer, server_default=text("0"), nullable=False, comment="声纹不一致组数")
    voice_compare_reviewed_num = Column(Integer, server_default=text("0"), nullable=False, comment="人工复核声纹比对组数")
    voice_inconsistent_reviewed_num = Column(Integer, server_default=text("0"), nullable=False, comment="人工复核声纹不一致组数")
    qa_type = Column(SmallInteger, server_default="", nullable=False, comment="质检类型")


class QaRule(MysqlBase, QaRuleBase):
    __tablename__ = "qa_rule"

    mobile_id = Column(SmallInteger, server_default=text("0"), nullable=False, comment="中间号一级移动id")
    channel_id = Column(SmallInteger, server_default=text("0"), nullable=False, comment="中间号二级移动id")
    company_id = Column(SmallInteger, server_default=text("0"), nullable=False, comment="中间号接入企业id")
    customer_id = Column(SmallInteger, server_default=text("0"), nullable=False, comment="中间号终端客户id")
    eb_id = Column(String(10), server_default="", nullable=False, comment="中间号EB账户ID")
    scene = Column(String(10), server_default="", nullable=False, comment="中间号账户报备场景")
    access_platform = Column(SmallInteger, server_default=text("0"), comment="中间号业务类型 0-中间号 1-云商号")
    is_eb_business = Column(SmallInteger, server_default=text("0"), comment="是否双平台 0-否 1-是")

    is_default = Column(String(1), server_default="0", nullable=False, comment="是否默认规则 0-否 1-是")
    rule_version = Column(String(1), server_default="0", nullable=False, comment="规则版本 1-模板规则 0-质检规则")

    def __init__(self):
        self.creator = "system"
        self.modifier = "system"
        self.status = "1"


class QaTask(MysqlBase, QaTaskBase):
    __tablename__ = "qa_task"

    is_stream = Column(SmallInteger, comment="是否实时流 0-否 1-是")
    end_flag = Column(SmallInteger, comment="是否结束片 0-否 1-是")

    rule = relationship("QaRule", uselist=False, foreign_keys="[QaTask.rule_id]",
                        primaryjoin="QaTask.rule_id == QaRule.id")

    def to_dict(self, with_rule=True):
        result = {c.name: getattr(self, c.name) for c in self.__table__.columns if c.name != "rule"}
        if with_rule:
            result["rule"] = self.rule.to_dict()
        return result


class QaFail(MysqlBase):
    """质检失败重试记录（MySQL / vi_cdr）"""
    __tablename__ = "qa_fail"
    __table_args__ = (
        UniqueConstraint("task_id", "data_id", name="uk_task_data"),
        {"mysql_engine": "InnoDB"},
    )

    id = Column(BigInteger, primary_key=True, autoincrement=False, comment="记录ID")
    create_time = Column(DateTime, server_default=text("CURRENT_TIMESTAMP"), nullable=False, comment="创建时间")
    update_time = Column(
        DateTime,
        server_default=text("CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"),
        nullable=False,
        comment="更新时间",
    )
    task_create_time = Column(DateTime, nullable=True, comment="任务创建时间")
    retry_time = Column(DateTime, nullable=True, comment="最近一次发起重试时间")
    app_id = Column(String(50), server_default="", nullable=False, comment="渠道id")
    qa_mobile_id = Column(Integer, nullable=False, comment="质检账户-一级移动ID")
    qa_channel_id = Column(Integer, nullable=False, comment="质检账户-二级移动ID")
    qa_company_id = Column(Integer, nullable=False, comment="质检账户-接入企业ID")
    qa_customer_id = Column(Integer, nullable=False, comment="质检账户-终端客户ID")
    task_id = Column(String(50), server_default="", nullable=False, comment="总任务id")
    data_id = Column(String(50), server_default="", nullable=False, comment="子任务id")
    url = Column(String(256), server_default="", nullable=False, comment="录音下载地址")
    remark1 = Column(String(256), server_default="", nullable=False, comment="客户侧信息")
    remark2 = Column(String(256), server_default="", nullable=False, comment="客户侧信息")
    remark3 = Column(String(256), server_default="", nullable=False, comment="客户侧信息")
    remark4 = Column(String(256), server_default="", nullable=False, comment="客户侧信息")
    user_data = Column(String(2048), server_default="", nullable=False, comment="专线质检透传数据")
    caller = Column(String(32), server_default="", nullable=False, comment="真实主叫")
    callee = Column(String(32), server_default="", nullable=False, comment="真实被叫")
    retry_count = Column(SmallInteger, server_default=text("0"), nullable=False, comment="重试次数，最多5次")
    retry_status = Column(
        SmallInteger,
        server_default=text("0"),
        nullable=False,
        comment="重试状态：0：待重试/回调已返回 1：重试中 2：成功 3：失败",
    )
    is_done = Column(SmallInteger, server_default=text("0"), nullable=False, comment="是否重试结束：0：否 1：是")
    rule_id = Column(Integer, nullable=False, comment="质检规则ID")
    qa_type = Column(SmallInteger, server_default=text("0"), nullable=False, comment="1-普通语音质检;2-专线质检")

    def to_dict(self):
        return {c.name: getattr(self, c.name) for c in self.__table__.columns}


class QaWorkPackage(TableMixin, MysqlBase):
    __tablename__ = "qa_word_package"

    app_id = Column(String(10), server_default="", nullable=False, comment="质检应用id")
    words = Column(String(2048), server_default="", nullable=False, comment="词袋内容")
    name = Column(String(100), server_default="", nullable=False, comment="词袋名称")

    def to_dict(self):
        return {c.name: getattr(self, c.name) for c in self.__table__.columns}


class QaScriptGroup(TableMixin, MysqlBase):
    __tablename__ = "qa_script_group"

    app_id = Column(String(10), server_default="", nullable=False, comment="质检应用id")
    name = Column(String(100), server_default="", nullable=False, comment="话术组名称")

    def to_dict(self):
        return {c.name: getattr(self, c.name) for c in self.__table__.columns}


class QaScript(TableMixin, MysqlBase):
    __tablename__ = "qa_script"

    app_id = Column(String(10), server_default="", nullable=False, comment="质检应用id")
    script = Column(String(2048), server_default="", nullable=False, comment="话术脚本")
    name = Column(String(100), server_default="", nullable=False, comment="话术名称")
    group_id = Column(Integer, server_default=text("0"), nullable=False, comment="话术组ID")

    def to_dict(self):
        return {c.name: getattr(self, c.name) for c in self.__table__.columns}


class QaDetail(CkBase, QaDetailBase):
    __tablename__ = "qa_detail"
    metadata = mid_qa_detail

    ext_num = Column(types.String, server_default="", nullable=False, comment="分机号")
    mobile_id = Column(types.Int32, comment="一级移动ID")
    channel_id = Column(types.Int32, comment="二级移动ID")
    company_id = Column(types.Int32, comment="接入企业ID")
    customer_id = Column(types.Int32, comment="终端客户ID")
    eb_id = Column(types.String, server_default="", comment="EB侧企业ID")
    zjh_scene = Column(types.String, server_default="", comment="中间号企业报备场景")
    is_eb_business = Column(types.Int32, comment="是否双平台 1-是 0-否")
    access_platform = Column(types.Int32, comment="业务类型 0-中间号 1-云商号")
    is_media_channel_inversed = Column(types.Int32, comment="是否声道反转")
    is_reviewed = Column(types.String, server_default="0", nullable=False, comment="是否已被复核 0-否 1-是")
    fraud_reviewed_type = Column(types.String, server_default="", nullable=False,
                                 comment="涉诈复核结果 1-主叫涉诈 2-被叫涉诈 3-第三方涉诈 0-不涉诈")
    fraud_reviewed_comment = Column(types.String, server_default="", nullable=False, comment="涉诈复核说明")

    def to_dict(self):
        data = {c.name: getattr(self, c.name) for c in self.__table__.columns}
        # data["`text`"] = data.pop("text", "")
        return data
    
    def update_attr(self, data: dict):
        for k, v in data.items():
            if k in (self.__table__.columns):
                setattr(self, k, v)


class QaSegment(CkBase, QaDetailBase):
    """分片质检结果，结构与 qa_detail 一致。"""
    __tablename__ = "qa_segment"
    metadata = mid_qa_segment

    ext_num = Column(types.String, server_default="", nullable=False, comment="分机号")
    mobile_id = Column(types.Int32, comment="一级移动ID")
    channel_id = Column(types.Int32, comment="二级移动ID")
    company_id = Column(types.Int32, comment="接入企业ID")
    customer_id = Column(types.Int32, comment="终端客户ID")
    eb_id = Column(types.String, server_default="", comment="EB侧企业ID")
    zjh_scene = Column(types.String, server_default="", comment="中间号企业报备场景")
    is_eb_business = Column(types.Int32, comment="是否双平台 1-是 0-否")
    access_platform = Column(types.Int32, comment="业务类型 0-中间号 1-云商号")
    is_media_channel_inversed = Column(types.Int32, comment="是否声道反转")
    is_reviewed = Column(types.String, server_default="0", nullable=False, comment="是否已被复核 0-否 1-是")
    fraud_reviewed_type = Column(types.String, server_default="", nullable=False,
                                 comment="涉诈复核结果 1-主叫涉诈 2-被叫涉诈 3-第三方涉诈 0-不涉诈")
    fraud_reviewed_comment = Column(types.String, server_default="", nullable=False, comment="涉诈复核说明")

    def to_dict(self):
        data = {c.name: getattr(self, c.name) for c in self.__table__.columns}
        return data

    def update_attr(self, data: dict):
        for k, v in data.items():
            if k in (self.__table__.columns):
                setattr(self, k, v)


class DccQaRule(MysqlBase, QaRuleBase):
    __tablename__ = "dcc_qa_rule"

    dcc_mobile_id = Column(String(10), server_default="", nullable=False, comment="中间号一级移动id")
    dcc_channel_id = Column(String(10), server_default="", nullable=False, comment="中间号二级移动id")
    dcc_platform_id = Column(SmallInteger, server_default=text("0"), nullable=False, comment="中间号应用平台id")
    dcc_user_id = Column(SmallInteger, server_default=text("0"), nullable=False, comment="中间号业务使用方id")
    dcc_app_id = Column(String(10), server_default="", nullable=False, comment="中间号EB账户ID")
    dcc_scene = Column(String(10), server_default="", nullable=False, comment="中间号账户报备场景")


class DccQaTask(MysqlBase, QaTaskBase):
    __tablename__ = "dcc_qa_task"

    rule = relationship("DccQaRule", uselist=False, foreign_keys="[DccQaTask.rule_id]",
                        primaryjoin="DccQaTask.rule_id == DccQaRule.id")

    def to_dict(self, with_rule=True):
        result = {c.name: getattr(self, c.name) for c in self.__table__.columns if c.name != "rule"}
        if with_rule:
            result["rule"] = self.rule.to_dict()
        return result


class DccQaDetail(CkBase, QaDetailBase):
    __tablename__ = "qa_detail"
    metadata = dcc_qa_detail

    is_media_channel_reversed = Column(types.Int32, comment="是否声道反转")
    dcc_mobile_id = Column(types.String, comment="中间号一级移动ID")
    dcc_channel_id = Column(types.String, comment="中间号二级移动ID")
    dcc_platform_id = Column(types.Int32, comment="中间号应用平台ID")
    dcc_user_id = Column(types.Int32, comment="中间号业务使用方ID")
    dcc_app_id = Column(types.String, server_default="", comment="中间号终端应用ID")
    dcc_scene = Column(types.Int32, server_default="", comment="中间号企业报备场景")

    def to_dict(self):
        data = {c.name: getattr(self, c.name) for c in self.__table__.columns}
        # data["`text`"] = data.pop("text", "")
        return data
    
    def update_attr(self, data: dict):
        for k, v in data.items():
            if k in (self.__table__.columns):
                setattr(self, k, v)


class MobileTab(MysqlBase):
    __tablename__ = "mobile_tab"

    id: Mapped[int] = mapped_column(
        Integer,
        primary_key=True,
        autoincrement=True,
        comment="主键id",
    )
    name: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        comment="企业英文名（可用拼音代替）",
    )
    nickname: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        server_default=text("''"),
        comment="企业中文简称",
        unique=True,  # 对应 uk_nickname
    )
    fullname: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        comment="企业中文全称",
    )
    role_id_list: Mapped[str] = mapped_column(
        String(100),
        nullable=True,
        server_default=text("''"),
        comment="关联角色id列表，以逗号分隔",
    )
    creator: Mapped[str] = mapped_column(
        String(50),
        nullable=False,
        comment="创建者",
    )
    comment: Mapped[str] = mapped_column(
        String(100),
        nullable=True,
        server_default=text("''"),
        comment="备注",
    )
    create_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        server_default=text("CURRENT_TIMESTAMP"),
        server_onupdate=text("CURRENT_TIMESTAMP"),
        comment="创建时间",
    )
    update_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        server_default=text("CURRENT_TIMESTAMP"),
        server_onupdate=text("CURRENT_TIMESTAMP"),
        comment="更新时间",
    )


class ChannelTab(MysqlBase):
    __tablename__ = "channel_tab"

    id: Mapped[int] = mapped_column(
        Integer,
        primary_key=True,
        autoincrement=True,
        comment="主键id",
    )
    name: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        comment="企业英文名（可用拼音代替）",
    )
    nickname: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        server_default=text("''"),
        unique=True,  # uk_nickname
        comment="企业中文简称",
    )
    fullname: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        comment="企业中文全称",
    )
    parent_id: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        comment="父级企业ID",
    )
    role_id_list: Mapped[str] = mapped_column(
        String(100),
        nullable=True,
        server_default=text("''"),
        comment="关联角色id列表，以逗号分隔",
    )
    creator: Mapped[str] = mapped_column(
        String(50),
        nullable=False,
        comment="创建者",
    )
    comment: Mapped[str] = mapped_column(
        String(100),
        nullable=True,
        server_default=text("''"),
        comment="备注",
    )
    create_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        server_default=text("CURRENT_TIMESTAMP"),
        comment="创建时间",
    )
    update_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        server_default=text("CURRENT_TIMESTAMP"),
        server_onupdate=text("CURRENT_TIMESTAMP"),
        comment="更新时间",
    )


class CompanyTab(MysqlBase):
    __tablename__ = "company_tab"

    id: Mapped[int] = mapped_column(
        Integer,
        primary_key=True,
        autoincrement=True,
        comment="主键id",
    )
    name: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        comment="企业英文名（可用拼音代替）",
    )
    nickname: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        server_default=text("''"),
        unique=True,  # uk_nickname
        comment="企业中文简称",
    )
    fullname: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        comment="企业中文全称",
    )
    app_key: Mapped[str] = mapped_column(
        String(50),
        nullable=False,
        unique=True,  # uk_app_key
        comment="企业鉴权id",
    )
    app_secret: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        unique=True,  # uk_app_secret
        comment="企业鉴权秘钥",
    )
    parent_id: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        comment="父级企业ID",
    )
    status: Mapped[int] = mapped_column(
        SmallInteger,  # 对应 tinyint(1)
        nullable=False,
        server_default=text("3"),
        comment="状态，枚举值：1-启用、2-停用、3-注销",
    )
    role_id_list: Mapped[str] = mapped_column(
        String(100),
        nullable=True,
        server_default=text("''"),
        comment="关联角色id列表，以逗号分隔",
    )
    creator: Mapped[str] = mapped_column(
        String(50),
        nullable=False,
        server_default=text("''"),
        comment="创建者",
    )
    comment: Mapped[str] = mapped_column(
        String(100),
        nullable=True,
        server_default=text("''"),
        comment="备注",
    )
    create_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        server_default=text("CURRENT_TIMESTAMP"),
        comment="创建时间",
    )
    update_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        server_default=text("CURRENT_TIMESTAMP"),
        server_onupdate=text("CURRENT_TIMESTAMP"),
        comment="更新时间",
    )


class CustomerTab(MysqlBase):
    __tablename__ = "customer_tab"

    id: Mapped[int] = mapped_column(
        Integer,
        primary_key=True,
        autoincrement=True,
        comment="主键id",
    )
    name: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        comment="企业英文名（可用拼音代替）",
    )
    nickname: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        server_default=text("''"),
        unique=True,  # uk_nickname
        comment="企业中文简称",
    )
    fullname: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        comment="企业中文全称",
    )
    parent_id: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        comment="父级企业ID",
    )
    status: Mapped[int] = mapped_column(
        SmallInteger,
        nullable=False,
        server_default=text("3"),
        comment="状态，枚举值：1-启用、2-停用、3-注销、4-超用量停用",
    )
    ability: Mapped[int | None] = mapped_column(
        SmallInteger,
        nullable=True,
        comment="关联原子能力，离线 asr-1，实时 asr-2，质检-3",
    )
    role_id_list: Mapped[str | None] = mapped_column(
        String(100),
        nullable=True,
        server_default=text("''"),
        comment="关联角色id列表，以逗号分隔",
    )
    creator: Mapped[str] = mapped_column(
        String(50),
        nullable=False,
        server_default=text("''"),
        comment="创建者",
    )
    comment: Mapped[str | None] = mapped_column(
        Text,
        nullable=True,
        comment="备注",
    )
    create_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        server_default=text("CURRENT_TIMESTAMP"),
        comment="创建时间",
    )
    update_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        server_default=text("CURRENT_TIMESTAMP"),
        server_onupdate=text("CURRENT_TIMESTAMP"),
        comment="更新时间",
    )
    ability_status: Mapped[int | None] = mapped_column(
        SmallInteger,
        nullable=True,
        server_default=text("1"),
    )
    app_id: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        comment="终端ID",
    )
    platform_app_id: Mapped[str | None] = mapped_column(
        String(20),
        nullable=True,
        comment="能开平台对应的appId",
    )
    priority: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        comment="优先级",
    )
    url: Mapped[str | None] = mapped_column(
        String(255),
        nullable=True,
        comment="质检复核回调地址",
    )
    is_reuse_asr: Mapped[int | None] = mapped_column(
        SmallInteger,
        nullable=True,
        comment="是否复用历史 ASR 文本 1-是 0-否",
    )
    source: Mapped[int | None] = mapped_column(
        SmallInteger,
        nullable=True,
        comment="1-能开 2-后台添加",
    )
    customer_type: Mapped[int | None] = mapped_column(
        Integer,
        nullable=True,
        comment="1-正式(formal) 2-试用(trial) 3-联调(test)",
    )
    total_minutes: Mapped[int | None] = mapped_column(
        Integer,
        nullable=True,
        comment="质检分钟总数，充值后更新，范围 1-1000000",
    )
    used_minutes: Mapped[float | None] = mapped_column(
        Float,
        nullable=True,
        server_default=text("0"),
        comment="已用质检分钟，定时任务扫描后更新",
    )
    validity: Mapped[int | None] = mapped_column(
        Integer,
        nullable=True,
        comment="有效期,范围：0-180",
    )
    expire_time: Mapped[datetime | None] = mapped_column(
        DateTime,
        nullable=True,
        comment="失效时间",
    )
    account_type: Mapped[str | None] = mapped_column(
        String(1),
        nullable=True,
        comment="客户类型 1-外部客户 2-内部客户",
    )
    result_push_url: Mapped[str] = mapped_column(
        String(255),
        nullable=False,
        server_default=text("''"),
        comment="专线质检结果推送地址(限制只允许https)",
    )
    bill_mode: Mapped[int] = mapped_column(
        SmallInteger,
        nullable=False,
        server_default=text("1"),
        comment="批价方式 0-不批价 1-实时批价 2-定时批价",
    )
    scenario_id: Mapped[str | None] = mapped_column(
        String(250),
        nullable=True,
        comment="场景Id，多个用逗号隔开",
    )
    script_id: Mapped[int | None] = mapped_column(
        Integer,
        nullable=True,
        comment="话术id",
    )
    qa_type: Mapped[int] = mapped_column(
        SmallInteger,
        nullable=False,
        server_default=text("1"),
        comment="1-普通语音质检;2-专线质检",
    )
    num_callback_url: Mapped[str | None] = mapped_column(
        String(255),
        nullable=True,
        server_default=text("''"),
        comment="号码操作结果回调地址",
    )
    manager_name: Mapped[str | None] = mapped_column(
        String(255),
        nullable=True,
        server_default=text("'无'"),
        comment="客户经理姓名",
    )
    manager_phone: Mapped[str | None] = mapped_column(
        String(255),
        nullable=True,
        server_default=text("'无'"),
        comment="客户经理手机号",
    )
    is_segment: Mapped[int | None] = mapped_column(
        SmallInteger,
        nullable=True,
        comment="质检模式 0-专线质检 - 离线质检；1-专线质检 - 实时质检；2-纯话务；3-行业语音质检",
    )
    qa_direction: Mapped[int | None] = mapped_column(
        SmallInteger,
        nullable=True,
        server_default=text("3"),
        comment="质检对象：1-主叫，2-被叫，3-主被叫，4-业务订购方（70014前缀为主叫，工作号签约为签约方）",
    )
    rule_id: Mapped[int | None] = mapped_column(
        Integer,
        nullable=True,
        comment="质检规则ID",
    )
    call_duration_t1: Mapped[int | None] = mapped_column(
        Integer,
        nullable=True,
        server_default=text("10"),
        comment="单位秒，小于等于于此时间且需要实时质检的通话，根据短通话策略筛选后发起质检",
    )
    call_duration_t2: Mapped[int | None] = mapped_column(
        Integer,
        nullable=True,
        server_default=text("60"),
        comment="单位秒，触发长通话分片实时质检的开始时间，大于此时间且需要实时质检的通话，将按照t3时间进行分片实时质检",
    )
    call_segment_t3: Mapped[int | None] = mapped_column(
        Integer,
        nullable=True,
        server_default=text("30"),
        comment="单位秒，实时质检分片时间",
    )
    sampling_ratio_p1: Mapped[int | None] = mapped_column(
        SmallInteger,
        nullable=True,
        server_default=text("5"),
        comment="短通话准实时质检抽检比例（开启实时质检时），取值范围：0~100",
    )
    sampling_ratio_p2: Mapped[int | None] = mapped_column(
        SmallInteger,
        nullable=True,
        server_default=text("0"),
        comment="短通话离线质检抽检比例（开启实时质检时），取值范围：0~100",
    )
    sampling_ratio_p3: Mapped[int | None] = mapped_column(
        SmallInteger,
        nullable=True,
        server_default=text("100"),
        comment="中通话准实时质检质检抽检比例（开启实时质检时），取值范围：0~100",
    )
    sampling_ratio_p: Mapped[int | None] = mapped_column(
        SmallInteger,
        nullable=True,
        server_default=text("50"),
        comment="短通话离线质检抽检比例（关闭实时质检时），取值范围：0~100",
    )
    random_sampling_ratio: Mapped[int | None] = mapped_column(
        SmallInteger,
        nullable=True,
        server_default=text("100"),
        comment="随机抽检比例",
    )
    need_random_sampling: Mapped[int] = mapped_column(
        SmallInteger,
        nullable=False,
        server_default=text("0"),
        comment="是否随机抽检：0-否；1-是",
    )
    caller_block_play_code: Mapped[str | None] = mapped_column(
        String(200),
        nullable=True,
        comment="黑名单拦截放音码",
    )
    is_mid_qc: Mapped[int] = mapped_column(
        SmallInteger,
        nullable=False,
        server_default=text("0"),
        comment="中间号内控策略: 1-是；0-否",
    )
    is_call: Mapped[int | None] = mapped_column(
        SmallInteger,
        nullable=True,
        server_default=text("0"),
        comment="是否开启话务质检：1-是,0-否",
    )
    stream_priority: Mapped[int | None] = mapped_column(
        Integer,
        nullable=True,
        server_default=text("9"),
        comment="实时分片优先级",
    )
    mode_status: Mapped[int | None] = mapped_column(
        SmallInteger,
        nullable=True,
        comment="能力状态 0-关闭，1-开启（同步自 qa_mode.mode_status 与 ability_status 的与操作）",
    )


class QaSubject(MysqlBase):
    __tablename__ = "qa_subject"

    id: Mapped[int] = mapped_column(
        Integer,
        primary_key=True,
        autoincrement=True,
        comment="主键id",
    )
    mobile_name: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        comment="一级企业全称",
    )
    mobile_nickname: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        server_default=text("''"),
        comment="一级企业简称",
    )
    channel_name: Mapped[str | None] = mapped_column(
        String(100),
        nullable=True,
        comment="二级企业全称",
    )
    channel_nickname: Mapped[str | None] = mapped_column(
        String(100),
        nullable=True,
        comment="二级企业简称",
    )
    company_name: Mapped[str | None] = mapped_column(
        String(100),
        nullable=True,
        comment="三级企业全称",
    )
    company_nickname: Mapped[str | None] = mapped_column(
        String(100),
        nullable=True,
        comment="三级企业简称",
    )
    customer_name: Mapped[str | None] = mapped_column(
        String(100),
        nullable=True,
        comment="终端全称",
    )
    customer_nickname: Mapped[str | None] = mapped_column(
        String(100),
        nullable=True,
        comment="终端简称",
    )
    ability: Mapped[int | None] = mapped_column(
        SmallInteger,
        nullable=True,
        comment="关联原子能力，离线asr-1，实时asr-2，质检-3",
    )
    ability_status: Mapped[int | None] = mapped_column(
        SmallInteger,
        nullable=True,
        comment="能力状态",
    )
    app_id: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        unique=True,  # uk_app_id
        comment="终端ID",
    )
    platform_app_id: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        server_default=text("'retention'"),
        comment="能开平台对应的appId",
    )
    app_key: Mapped[str] = mapped_column(
        String(50),
        nullable=False,
        comment="企业鉴权id",
    )
    app_secret: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        comment="企业鉴权秘钥",
    )
    qa_type: Mapped[int] = mapped_column(
        SmallInteger,
        nullable=False,
        server_default=text("1"),
        comment="质检类型：1-普通语音质检;2-专线质检",
    )
    priority: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        comment="优先级",
    )
    url: Mapped[str] = mapped_column(
        String(255),
        nullable=False,
        server_default=text("''"),
        comment="质检复核回调地址",
    )
    is_reuse_asr: Mapped[int] = mapped_column(
        SmallInteger,
        nullable=False,
        server_default=text("0"),
        comment="是否复用历史ASR文本 1-是 0-否",
    )
    result_push_url: Mapped[str] = mapped_column(
        String(255),
        nullable=False,
        server_default=text("''"),
        comment="专线质检结果推送地址",
    )
    source: Mapped[int] = mapped_column(
        SmallInteger,
        nullable=False,
        server_default=text("2"),
        comment="1-能开;2-后台添加",
    )
    customer_type: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        server_default=text("2"),
        comment="1-正式(formal);2-试用(trial);3-联调(test)",
    )
    account_type: Mapped[str] = mapped_column(
        String(1),
        nullable=False,
        server_default=text("'1'"),
        comment="客户类型 1-外部客户 2-内部客户",
    )
    bill_mode: Mapped[int | None] = mapped_column(
        SmallInteger,
        nullable=True,
        comment="批价方式 0-不批价 1-实时批价 2-定时批价",
    )
    total_minutes: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        server_default=text("0"),
        comment="质检分钟总数，充值后更新，范围1-100000",
    )
    used_minutes: Mapped[float | None] = mapped_column(
        Float,
        nullable=True,
        server_default=text("0"),
        comment="已用质检分钟，定时任务扫描后更新",
    )
    validity: Mapped[int | None] = mapped_column(
        Integer,
        nullable=True,
        comment="有效期,范围：0-180",
    )
    expire_time: Mapped[datetime | None] = mapped_column(
        DateTime,
        nullable=True,
        comment="失效时间",
    )
    creator: Mapped[str] = mapped_column(
        String(50),
        nullable=False,
        server_default=text("''"),
        comment="创建者",
    )
    comment: Mapped[str | None] = mapped_column(
        Text,
        nullable=True,
        comment="备注",
    )
    create_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        server_default=text("CURRENT_TIMESTAMP"),
        comment="创建时间",
    )
    update_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        server_default=text("CURRENT_TIMESTAMP"),
        server_onupdate=text("CURRENT_TIMESTAMP"),
        comment="更新时间",
    )
    rule_id: Mapped[int | None] = mapped_column(
        Integer,
        nullable=True,
        comment="质检规则ID",
    )
    is_segment: Mapped[int | None] = mapped_column(
        SmallInteger,
        nullable=True,
        comment="质检是否需要通话过程中实时分片质检：-1-不涉及;0-准实时",
    )
    status: Mapped[int] = mapped_column(
        SmallInteger,
        nullable=False,
        server_default=text("1"),
        comment="状态，枚举值：1-启用、2-停用、3-注销",
    )
    is_bill: Mapped[int | None] = mapped_column(
        SmallInteger,
        nullable=True,
        comment="是否批价: 1-是；0-否",
    )
    stream_priority: Mapped[int | None] = mapped_column(
        Integer,
        nullable=True,
        server_default=text("9"),
        comment="实时分片优先级",
    )
    bill_id: Mapped[int | None] = mapped_column(
        Integer,
        nullable=True,
        comment="批价ID",
    )
    is_mid_qc: Mapped[int] = mapped_column(
        SmallInteger,
        nullable=False,
        server_default=text("0"),
        comment="中间号内控策略: 1-是；0-否",
    )


class QaEnterprise(MysqlBase):
    __tablename__ = "qa_enterprise"

    id: Mapped[int] = mapped_column(
        Integer,
        primary_key=True,
        autoincrement=True,
        comment="主键id",
    )
    region_id: Mapped[str] = mapped_column(
        String(10),
        nullable=False,
        server_default=text("''"),
        comment="报备企业/省市唯一ID",
    )
    fullname: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        server_default=text("''"),
        comment="报备企业/省市全称",
    )
    nickname: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        server_default=text("''"),
        comment="报备企业/省市简称",
    )
    parent_id: Mapped[str] = mapped_column(
        String(10),
        nullable=True,
        server_default=text("''"),
        comment="报备企业/省市父级ID",
    )
    app_id: Mapped[str] = mapped_column(
        String(10),
        nullable=False,
        server_default=text("''"),
        comment="报备企业/省市所属终端ID",
    )
    level: Mapped[str] = mapped_column(
        String(1),
        nullable=False,
        server_default=text("''"),
        comment="所属企业层级 1-一级移动 2-二级移动 3-接入企业 4-终端客户",
    )
    main_id: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        comment="所属企业层级对应主键ID",
    )
    create_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        server_default=text("CURRENT_TIMESTAMP"),
        comment="创建时间",
    )
    update_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        server_default=text("CURRENT_TIMESTAMP"),
        server_onupdate=text("CURRENT_TIMESTAMP"),
        comment="更新时间",
    )


class BizNumAuth(MysqlBase):
    __tablename__ = "biz_num_auth"

    id: Mapped[int] = mapped_column(
        Integer,
        primary_key=True,
        autoincrement=True,
        comment="主键id",
    )
    num: Mapped[str] = mapped_column(
        String(50),
        nullable=False,
        server_default=text("''"),
        comment="报备企业/省市唯一ID",
    )
    match_type: Mapped[int] = mapped_column(
        SmallInteger,
        nullable=False,
        server_default=text("''"),
        comment="匹配类型",
    )
    customer_id: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        server_default=text("''"),
        comment="终端Id",
    )
    company_id: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        server_default=text("''"),
        comment="接入企业Id",
    )
    channel_id: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        server_default=text("''"),
        comment="二级移动Id",
    )
    mobile_id: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        server_default=text("''"),
        comment="一级移动Id",
    )
    operator: Mapped[str] = mapped_column(
        String(255),
        nullable=False,
        comment="操作人",
    )
    create_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        server_default=text("CURRENT_TIMESTAMP"),
        comment="创建时间",
    )
    update_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        server_default=text("CURRENT_TIMESTAMP"),
        server_onupdate=text("CURRENT_TIMESTAMP"),
        comment="更新时间",
    )

    def __init__(self, num, mobile_id, channel_id, company_id, customer_id, match_type):
        self.num = num
        self.mobile_id = mobile_id
        self.channel_id = channel_id
        self.company_id = company_id
        self.customer_id = customer_id
        self.match_type = match_type
        self.operator = "system"


class EnterpriseScene(MysqlBase):
    """专线质检场景表：场景编码 → 词袋ID。"""
    __tablename__ = "enterprise_scene"

    id: Mapped[int] = mapped_column(
        Integer,
        primary_key=True,
        autoincrement=True,
        comment="唯一id",
    )
    create_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        server_default=text("CURRENT_TIMESTAMP"),
        comment="创建时间",
    )
    update_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        server_default=text("CURRENT_TIMESTAMP"),
        server_onupdate=text("CURRENT_TIMESTAMP"),
        comment="更新时间",
    )
    code: Mapped[str] = mapped_column(
        String(10),
        nullable=False,
        server_default=text("''"),
        comment="场景编码",
    )
    name: Mapped[str] = mapped_column(
        String(32),
        nullable=False,
        server_default=text("''"),
        comment="场景名称",
    )
    word_package_id: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        server_default=text("''"),
        comment="关键词Id，多个则使用英文逗号隔开",
    )

