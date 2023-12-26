#!/usr/bin/env bash

# 验证
KEY="SQuFDTfLzna0vZSgnCx4QMKNMEXEGMJMMtRIP4H0qZ3xybPjaXcHLGDxr4IcPkYKv8wElkJ2GK1n75xVf0uhLZRPPMTVEU3TQrONHD2lttI1Z4Xl9tMSbUV2ukT80wy6"
# 指定克隆分支
BRANCH_TAG="v1.2.1"
# 镜像别名
IMG_NAME="bonc_v1.2.1"
# 编译服务器地址
SERVER_ADDR="124.71.62.21"

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
  "region":"cn-south-1",
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
