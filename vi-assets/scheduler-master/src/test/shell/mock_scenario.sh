#!/bin/bash
payload=`rabbitmqadmin -f raw_json get queue=scheduler.scenario.queue ackmode=ack_requeue_true`
scenario_request=`echo $payload | jq .[0].payload`
scenario_request=${scenario_request//\\\\/}
scenario_request=${scenario_request//\\\"/\"}
scenario_request=${scenario_request//\"\"/}
#jobId=$(echo scenario_request | jq .jobId)

#rabbitmqadmin publish routing_key=scheduler.asr.response.queue payload="{\"code\":\"0000\", \"message\":\"success\", \"data\":{\"jobId\":$jobId, \"text\":\"test，微信，转账，确实存在这个问题，该怎么办好\"}}"
rabbitmqadmin publish routing_key=scheduler.scenario.response.queue payload="{\"code\":\"0000\", \"message\":\"success\", \"data\":{\"jobId\":\"1858817209330176001\", \"result\":\"1\", \"consistency\":\"0\"}}"
#rabbitmqadmin publish routing_key=scheduler.asr.response.queue payload="{\"code\":\"0001\", \"message\":\"error\", \"data\":{\"jobId\":\"1812756577338396672\"}}"
