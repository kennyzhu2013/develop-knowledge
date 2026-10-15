from sqlalchemy import Column, Integer, String, DateTime, SmallInteger, text, Date

from apps.database import TableMixin, BASE_STRATEGY


class ComplaintDetail(TableMixin, BASE_STRATEGY):
    __tablename__ = "complaint_detail"
    id = Column(Integer, primary_key=True, comment="唯一编码")
    import_id = Column(String(20), nullable=False, comment="导入ID,TS+YYYYMMDD+3位序列号")
    complaint_id = Column(String(16), nullable=False, comment="投诉ID,ZJH+YYYYMMDD+3位序列号")
    complaint_number = Column(String(16), nullable=False, comment="投诉号码")
    complaint_number_fuzzy = Column(String(500), nullable=False, comment="模糊匹配投诉号码")
    complaint_operator = Column(String(20), server_default="", nullable=True, comment="投诉号码运营商")
    complaint_province = Column(String(10), server_default="", nullable=True, comment="投诉号码归属省")
    complaint_city = Column(String(20), server_default="", nullable=True, comment="投诉号码归属市")
    complained_number = Column(String(16), nullable=False, comment="被投诉号码")
    complained_province = Column(String(10), server_default="", nullable=True, comment="被投诉号码归属省")
    complained_city = Column(String(20), server_default="", nullable=True, comment="被投诉号码归属市")
    call_time = Column(DateTime, server_default=text("CURRENT_TIMESTAMP"), nullable=False, comment="来电时间")
    complaint_content = Column(String(255), server_default="", nullable=True, comment="投诉内容")
    real_caller_number = Column(String(500), server_default="", nullable=True, comment="真实主叫")
    mobile_id = Column(SmallInteger, comment="一级移动id")
    channel_id = Column(SmallInteger, comment="二级移动id")
    company_id = Column(SmallInteger, comment="接入企业id")
    customer_id = Column(SmallInteger, comment="终端客户id")
    eb_id = Column(String(20), comment="eb项目id")
    access_platform = Column(SmallInteger, server_default=text("0"), nullable=True, comment="接入账户类型：0 -中间号  1-云商号")
    communication_result = Column(SmallInteger, server_default=text("0"), nullable=True, comment="通联情况：0:未核查,1:有通联,2:无通联，有使用,3:无使用")
    call_id = Column(String(800), comment="话单id")
    call_status = Column(String(1200), comment="通话状态")
    asr_result = Column(String(255), comment="ASR结果")
    complaint_type = Column(SmallInteger, server_default=text("0"), nullable=True, comment="投诉分类")
    other_complaint_type = Column(SmallInteger, server_default=text("0"), nullable=True, comment="投诉分类（非订单类）")
    check_result_type = Column(SmallInteger, server_default=text("0"), nullable=True, comment="核查结果分类")
    system_result = Column(String(255), comment="系统质检核查结果")
    manual_content = Column(String(255), comment="人工质检复核内容")
    manual_result = Column(String(255), comment="人工质检复核结果")
    handle_opinions = Column(String(255), comment="处理意见")
    comment = Column(String(255), comment="备注")
    complaint_date = Column(Date, comment="投诉日期")
    username = Column(String(100), comment="核查人账户名称")
    complained_operator = Column(String(20), server_default="", nullable=True, comment="被投诉号码运营商")
    scene = Column(String(100), server_default="", nullable=True, comment="报备场景")
    qa_task_id = Column(String(100), server_default="", nullable=True, comment="质检任务ID")
    qa_status = Column(SmallInteger, server_default=text("-1"), nullable=True, comment="质检状态：0:失败,1:成功,2:进行中,-1:未开始")
    total = Column(Integer, comment="话单数量")
    manual_time = Column(DateTime, comment="核查时间")
    update_time = Column(DateTime, server_default=text("CURRENT_TIMESTAMP"), nullable=False, comment="更新时间")
    create_time = Column(DateTime, server_default=text("CURRENT_TIMESTAMP"), nullable=False, comment="创建时间")
    def to_dict(self):
        return {c.name: getattr(self, c.name) for c in self.__table__.columns}


class StrategyDetail(BASE_STRATEGY):
    __tablename__ = "strategy_detail"
    id = Column(Integer, primary_key=True, comment="主键")
    val = Column(String(255), default='', nullable=False, comment="目标值")
    level = Column(String(100), default='', nullable=False, comment="策略级别：number/prefix/company/customer")
    op_type = Column(String(1), default='', nullable=False, comment="操作指令 0挂断 1接通")
    call_play_code = Column(String(255), default='', nullable=False, comment="主叫放音")
    called_play_code = Column(String(255), default='', nullable=False, comment="主被叫放音")
    called_display_no = Column(String(255), default='', nullable=False, comment="号显配置，1为主叫，0为中间号")
    allow_callee_forwarding = Column(String(255), default='', nullable=False,
                                     comment="是否允许被叫呼转 0-拦截全部被叫呼转 1-限制被叫呼转号码个数（默认）2-不拦截被叫呼转")
    allow_forwarded_list = Column(String(255), default='', nullable=False, comment="被叫允许呼转号码列表,以英文逗号隔开")
    role = Column(String(255), default='', nullable=False, comment="号码类型，Level=customer/company时为空")
    company_id = Column(String(255), default='', nullable=False, comment="接入企业id，以英文逗号隔开")
    customer_id = Column(String(255), default='', nullable=False, comment="终端客户id，以英文逗号隔开")
    province_id = Column(String(255), default='', nullable=False, comment="省份id，以英文逗号隔开")
    city_id = Column(String(255), default='', nullable=False, comment="城市id，以英文逗号隔开")
    callernumber = Column(String(255), default='', nullable=False, comment="主叫号码")
    calleenumber = Column(String(255), default='', nullable=False, comment="被叫号码")
    midnumber = Column(String(255), default='', nullable=False, comment="中间号")
    displaynum = Column(String(255), default='', nullable=False, comment="被叫号显")

    service = Column(String(1), comment="管控级别 1:通话 2:短信 空值:通话+短信")

    expiration_time = Column(DateTime, comment="失效时间")
    strategy_name = Column(String(100), default='', nullable=False, comment="策略名称")

    create_time = Column(DateTime, default=text("CURRENT_TIMESTAMP"), nullable=True, comment="创建时间")
    update_time = Column(DateTime, default=text("CURRENT_TIMESTAMP"), nullable=True, comment="修改时间")

    def to_dict(self):
        return {c.name: getattr(self, c.name) for c in self.__table__.columns}


class ControlFrequencyThreshold(BASE_STRATEGY, TableMixin):
    __tablename__ = "control_frequency_threshold"
    customer_id = Column(Integer, nullable=False, comment="终端ID")
    is_a_hf = Column(SmallInteger, server_default=text("0"), nullable=True, comment="A高频管控开关;0:关,1:开")
    a_control = Column(Integer, comment="A高频阈值")
    a_duration = Column(Integer, comment="A达频管控时长")
    is_ab_hf = Column(SmallInteger, server_default=text("0"), nullable=True, comment="A同B高频管控开关;0:关,1:开")
    ab_control = Column(Integer, comment="A同B高频阈值")
    ab_duration = Column(Integer, comment="A同B达频管控时长")
    is_ax_hf = Column(SmallInteger, server_default=text("0"), nullable=True, comment="A同X高频管控开关;0:关,1:开")
    ax_control = Column(Integer, comment="A同X高频阈值")
    ax_duration = Column(Integer, comment="A同X达频管控时长")
    is_b_hf = Column(SmallInteger, server_default=text("0"), nullable=True, comment="B高频管控开关;0:关,1:开")
    b_control = Column(Integer, comment="B高频阈值")
    b_duration = Column(Integer, comment="B达频管控时长")
    is_xb_hf = Column(SmallInteger, server_default=text("0"), nullable=True, comment="X同B高频管控开关;0:关,1:开")
    xb_control = Column(Integer, comment="X同B高频阈值")
    xb_duration = Column(Integer, comment="X同B达频管控时长")
    is_worktime = Column(SmallInteger, server_default=text("0"), nullable=True, comment="工作时间管控开关;0:关,1:开")
    worktime_control = Column(String(50), comment="工作时间管控时间")
    is_b_handlestart = Column(SmallInteger, server_default=text("0"), nullable=True, comment="B呼叫高频管控开关;0:关,1:开")
    b_handlestart_control = Column(Integer, comment="B呼叫高频阈值")
    b_handlestart_duration = Column(Integer, comment="B呼叫高频达频管控时长")
    is_sms_hf = Column(SmallInteger, server_default=text("0"), nullable=True, comment="短信高频管控开关;0:关,1:开")
    sms_control = Column(Integer, comment="短信高频管控阈值")
    sms_duration = Column(Integer, comment="短信高频管控时长")

    def to_dict(self):
        return {c.name: getattr(self, c.name) for c in self.__table__.columns}


class StrategyInfo(TableMixin, BASE_STRATEGY):
    __tablename__ = "strategy_info"

    mobile_id = Column(SmallInteger, server_default=text("0"), nullable=False, comment="一级移动id")
    channel_id = Column(SmallInteger, server_default=text("0"), nullable=False, comment="二级移动id")
    company_id = Column(SmallInteger, server_default=text("0"), nullable=False, comment="接入企业id")
    customer_id = Column(SmallInteger, server_default=text("0"), nullable=False, comment="终端客户id")
    access_platform = Column(SmallInteger, server_default=text("0"), nullable=False, comment="接入账号类型 0中间号 1云商号")
    threshold = Column(Integer, comment="阈值")
    name = Column(String(100), server_default="", nullable=False, comment="策略名称，枚举类型")
    status = Column(String(1), server_default="", nullable=False, comment="状态，1正常 2暂停 3取消")
    period = Column(String(100), server_default="", nullable=False, comment="执行周期，格式：数字+字母，如1h")
    comment = Column(String(255), server_default="", nullable=False, comment="策略说明")

    def __init__(self, mobile_id, channel_id, company_id, customer_id, threshold, access_platform, name, status, period, comment):
        self.mobile_id = mobile_id
        self.channel_id = channel_id
        self.company_id = company_id
        self.customer_id = customer_id
        self.access_platform = access_platform
        self.name = name
        self.status = status
        self.period = period
        self.comment = comment
        self.threshold = threshold

    def to_dict(self):
        return {c.name: getattr(self, c.name) for c in self.__table__.columns}


class ComplaintGzhDetail(TableMixin, BASE_STRATEGY):
	__tablename__ = "complaint_gzh_detail"
	import_id = Column(String(20), nullable=False, comment="导入ID,DR+YYYYMMDD+3位序列号")
	complaint_id = Column(String(16), nullable=False, comment="投诉ID,HZH+YYYYMMDD+3位序列号")
	complaint_number = Column(String(16), nullable=False, comment="投诉号码")
	complaint_number_fuzzy = Column(String(500), nullable=False, comment="模糊匹配投诉号码")
	complaint_operator = Column(String(20), server_default="", nullable=True, comment="投诉号码运营商")
	complaint_province = Column(String(10), server_default="", nullable=True, comment="投诉号码归属省")
	complaint_city = Column(String(20), server_default="", nullable=True, comment="投诉号码归属市")
	complained_number = Column(String(16), nullable=False, comment="被投诉号码")
	complained_province = Column(String(10), server_default="", nullable=True, comment="被投诉号码归属省")
	complained_city = Column(String(20), server_default="", nullable=True, comment="被投诉号码归属市")
	call_time = Column(DateTime, server_default=text("CURRENT_TIMESTAMP"), nullable=False, comment="来电时间")
	complaint_content = Column(String(255), server_default="", nullable=True, comment="投诉内容")
	real_caller_number = Column(String(500), server_default="", nullable=True, comment="真实主叫")
	gzh_province = Column(String(50), comment="项目接入省份")
	gzh_channel = Column(String(50), comment="项目接入渠道")
	project_manager = Column(String(50), comment="项目经理")
	gzh_id = Column(String(50), comment="企业id")
	gzh_name = Column(String(50), comment="企业名称")
	gzh_scene = Column(String(100), server_default="", nullable=True, comment="企业场景")
	mobile_id = Column(SmallInteger, comment="一级移动id")
	channel_id = Column(SmallInteger, comment="二级移动id")
	company_id = Column(SmallInteger, comment="接入企业id")
	customer_id = Column(SmallInteger, comment="终端客户id")
	communication_result = Column(SmallInteger, server_default=text("0"), nullable=True, comment="通联情况：0:未核查,1:有通联,2:无通联，有使用,3:无使用")
	call_id = Column(String(800), comment="话单id")
	call_status = Column(String(1200), comment="通话状态")
	asr_result = Column(String(255), comment="ASR结果")
	complaint_type = Column(SmallInteger, server_default=text("0"), nullable=True, comment="投诉分类-订单类")
	check_scene_type = Column(SmallInteger, server_default=text("0"), nullable=True, comment="场景是否一致")
	check_result_type = Column(SmallInteger, server_default=text("0"), nullable=True, comment="核查与投诉内容是否一致")
	system_result = Column(String(255), comment="系统质检核查结果")
	manual_content = Column(String(255), comment="人工质检复核内容")
	manual_result = Column(String(255), comment="人工质检复核结果")
	handle_opinions = Column(String(255), comment="处理意见")
	comment = Column(String(255), comment="备注")
	complaint_date = Column(Date, comment="投诉日期")
	username = Column(String(100), comment="核查人账户名称")
	complained_operator = Column(String(20), server_default="", nullable=True, comment="被投诉号码运营商")
	scene = Column(String(100), server_default="", nullable=True, comment="报备场景")
	qa_task_id = Column(String(100), server_default="", nullable=True, comment="质检任务ID")
	qa_status = Column(SmallInteger, server_default=text("-1"), nullable=True, comment="质检状态：0:失败,1:成功,2:进行中,-1:未开始")
	total = Column(Integer, comment="话单数量")
	manual_time = Column(DateTime, comment="核查时间")

