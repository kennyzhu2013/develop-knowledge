#!/bin.test/bash

ba_request=$(rabbitmqadmin -f raw_json get queue=scheduler.ba.queue ackmode=ack_requeue_true)
ba_request=$(echo "$ba_request" | jq .[0].payload)
jobId=`echo  $ba_request | jq ".jobId"`
echo $jobId

rabbitmqadmin publish routing_key=scheduler.ba.response.queue  payload="{\"code\":\"0000\", \"message\":\"success\", \"data\":{\"jobId\":\"1805122997141508096\"}}"