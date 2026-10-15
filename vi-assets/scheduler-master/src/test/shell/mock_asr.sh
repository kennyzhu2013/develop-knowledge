#!/bin/bash
payload=`rabbitmqadmin -f raw_json get queue=scheduler.asr.queue ackmode=ack_requeue_true`
asr_request=`echo $payload | jq .[0].payload`
asr_request=${asr_request//\\\\/}
asr_request=${asr_request//\\\"/\"}
asr_request=${asr_request//\"\"/}
jobId=$(echo $asr_request | jq .jobId)

#rabbitmqadmin publish routing_key=scheduler.asr.response.queue payload="{\"code\":\"0000\", \"message\":\"success\", \"data\":{\"jobId\":$jobId, \"text\":\"test，微信，转账，确实存在这个问题，该怎么办好\"}}"
rabbitmqadmin publish routing_key=scheduler.asr.response.queue payload="{\"code\":\"0000\", \"message\":\"success\", \"data\":{\"jobId\":\"1815907788807868417\", \"text\":\"test，微信，转账，确实存在这个问题，该怎么办好\"}}"
#rabbitmqadmin publish routing_key=scheduler.asr.response.queue payload="{\"code\":\"0001\", \"message\":\"error\", \"data\":{\"jobId\":\"1812756577338396672\"}}"
