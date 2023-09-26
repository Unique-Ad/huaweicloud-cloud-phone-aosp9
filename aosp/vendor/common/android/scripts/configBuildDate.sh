#! /system/bin/sh

buildTime="$(getprop ro.build.date.utc)"
if [[ -n "${buildTime}" ]];then
  setprop ro.build.date "$(date -d @"${buildTime}")"
fi
