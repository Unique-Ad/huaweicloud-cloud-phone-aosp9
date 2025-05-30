#! /system/bin/sh

if [[ "`getprop com.cph.config_phone_done`" == "1" ]];then
    exit 0
fi

settings put secure adb_install_need_confirm 0

if [ 1 == "`getprop disable.status.bar`" ];then
	settings put global policy_control immersive.status=*
else
	settings put global policy_control null
fi

local android_id=`getprop android.id`
echo "$android_id" | grep -E "^[a-f0-9]{16}$"
local ret=$?
if [[ $ret -eq 0 ]]; then
    settings put secure android_id ${android_id}
fi

# run ni_rsrc_mon to init Netint encode card when we found encode card
[[ -b /dev/nvme0n1 ]] && /vendor/bin/ni_rsrc_mon


# extend funcion sh
[[ -f /system/bin/extend_func.sh ]] && timeout -s 9 10 sh /system/bin/extend_func.sh
[[ -f /data/local/tmp/extend_custom.sh ]] && timeout -s 9 10 sh /data/local/tmp/extend_custom.sh

setprop com.cph.config_phone_done 1
