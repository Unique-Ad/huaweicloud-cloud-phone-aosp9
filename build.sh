#!/usr/bin/env bash

# 验证
KEY="bIX7vyx6tjCK6pYx8LW9kiaGhxf0jSYEz6DNJtIQ3cWF8fK7AVG885v1M11CM8Ry3c5enJycg03CdnNP71AdpQ7ojYpBp97S2hqJTUHJ70xHqYbAwS0DX5nf9ZNAjaWd"
# 指定克隆分支
BRANCH_TAG="v5.0.1"
# 镜像别名
IMG_NAME="galaxy-aosp9-001"
# 编译服务器地址
SERVER_ADDR="139.9.90.128"

# 默认不执行增量编译
INCREMENTAL=0
while getopts ":il" opt; do
  case "${opt}" in
    i) INCREMENTAL=1;;
    l)
    curl -X POST -k --insecure -H "key: ${KEY}" https://${SERVER_ADDR}:8081/get_log
    exit 0;;
    ?)
    echo "input param error! exit."
    exit 1;;
  esac
done

echo '{
  "region":"cn-east-3",
  "tag":"'${BRANCH_TAG}'",
  "type":"aosp9",
  "name":"'${IMG_NAME}'",
  "incrementalBuild":"'${INCREMENTAL}'"
}' > curl_tmp.json

curl -X POST -k --insecure -H "Content-Type: application/json" -H "key: ${KEY}" -d @curl_tmp.json  https://${SERVER_ADDR}:8081/image/build &
build_pid=$!

function quit_build ()
{
  echo "quit command caught, stop building..."
  curl -X POST -k --insecure -H "key: ${KEY}" https://${SERVER_ADDR}:8081/stop_build
  kill -9 $build_pid
  echo '\n'
}
trap "quit_build" SIGINT

until false
do
    sleep 5
    is_running=$(ps | grep $build_pid)
    if [ "${is_running}" == "" ]; then
        rm curl_tmp.json
        break
    fi
    curl -X POST -k --insecure -H "key: ${KEY}" https://${SERVER_ADDR}:8081/get_log
done
