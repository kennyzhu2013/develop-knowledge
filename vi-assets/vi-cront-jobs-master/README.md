# vi-cront-jobs

专线质检策略定时脚本

# 每分钟执行 - 超业务范围
* * * * * export CRONT_ENV=test && /home/production/vi-cront-jobs/myenv/bin/python /home/production/vi-cront-jobs/viStrategyBusinessRange.py

# 每分钟执行 企业外呼门限
* * * * * export CRONT_ENV=test && /home/production/vi-cront-jobs/myenv/bin/python /home/production/vi-cront-jobs/viStrategyBusinessThreshold.py


# 每分钟执行 - 短时高频
* * * * * export CRONT_ENV=test && /home/production/vi-cront-jobs/myenv/bin/python /home/production/vi-cront-jobs/viStrategyHighFrequency.py

# 每分钟执行 - 长通话管控
* * * * * export CRONT_ENV=test && /home/production/vi-cront-jobs/myenv/bin/python /home/production/vi-cront-jobs/viStrategyLongCall.py

# 每10分钟执行 - 非工作时间管控
*/10 * * * * export CRONT_ENV=test && /home/production/vi-cront-jobs/myenv/bin/python /home/production/vi-cront-jobs/viStrategyOffwork.py

# 每分钟执行 - 异地外呼管控
* * * * * export CRONT_ENV=test && /home/production/vi-cront-jobs/myenv/bin/python /home/production/vi-cront-jobs/viStrategyRemoteRange.py

# 每天凌晨1点执行 - 日统计
0 1 * * * export CRONT_ENV=test && /home/production/vi-cront-jobs/myenv/bin/python /home/production/vi-cront-jobs/viStrategyDailyStatistics.py

# 每分钟执行 - 策略停用清理
* * * * * export CRONT_ENV=test && /home/production/vi-cront-jobs/myenv/bin/python /home/production/vi-cront-jobs/viStrategyDisabledRedisClear.py

# 每小时第5分钟执行非工作时间和企业外呼门限明细入库
5 * * * * export CRONT_ENV=test && /home/production/vi-cront-jobs/myenv/bin/python /home/production/vi-cront-jobs/viStrategyBillDetail.py


# 每天凌晨1点执行号码活跃更新
0 1 * * * export CRONT_ENV=test && /home/production/vi-cront-jobs/myenv/bin/python /home/production/vi-cront-jobs/viStrategyActivePhone.py

# 每分钟执行 - 跨区域大范围高频外呼管控
* * * * * export CRONT_ENV=test && /home/production/vi-cront-jobs/myenv/bin/python /home/production/vi-cront-jobs/viStrategyDifareaHighFreq.py

# 每分钟执行 - 沉默号码话务突增管控
* * * * * export CRONT_ENV=test && /home/production/vi-cront-jobs/myenv/bin/python /home/production/vi-cront-jobs/viStrategySilentCallBurst.py

# 每分钟执行 - 闲时高频外呼管控
* * * * * export CRONT_ENV=test && /home/production/vi-cront-jobs/myenv/bin/python /home/production/vi-cront-jobs/viStrategyIdleHighFreq.py

# 每分钟执行 - 短时长通话突增管控
* * * * * export CRONT_ENV=test && /home/production/vi-cront-jobs/myenv/bin/python /home/production/vi-cront-jobs/viStrategyShortLongcallBurst.py

# 每分钟执行 - 短时高频突增管控
* * * * * export CRONT_ENV=test && /home/production/vi-cront-jobs/myenv/bin/python /home/production/vi-cront-jobs/viStrategyShortHighfreqBurst.py

# 每分钟执行 - 沉默号码外地长通话突增管控
* * * * * export CRONT_ENV=test && /home/production/vi-cront-jobs/myenv/bin/python /home/production/vi-cront-jobs/viStrategySilentDifareaLongcall.py