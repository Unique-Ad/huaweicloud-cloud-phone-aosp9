#!/system/bin/sh

function copy_cpuinfo_for_exagear() {
  cp /proc/cpuinfo /system/etc/cpuinfo.host
  chmod 444 /system/etc/cpuinfo.host
}

function hook_cpu_features() {
  if [ -f /data/local/config/cpuinfo ];then
    chmod 644 /data/local/config/cpuinfo
    mount --bind /data/local/config/cpuinfo /proc/cpuinfo
  else
    if [[ -e /proc/sys/container/default_cpu_count ]];then
      return
    fi
    cat /proc/cpuinfo |sed 's/^Features[[:blank:]]*:[[:blank:]].*$/Features        : half thumb fastmult vfp edsp neon vfpv3 tls vfpv4 idiva idivt lpae evtstrm aes pmull sha1 sha2 crc32/g' > /system/cpuinfo_merge
    chmod 644 /system/cpuinfo_merge
    sed '1 iProcessor       : AArch64 Processor rev 1 (aarch64)' -i /system/cpuinfo_merge
    umount /proc/cpuinfo
    mount --bind /system/cpuinfo_merge /proc/cpuinfo
  fi
}

function hook_cpu() {
  if [ -d /data/local/config/cpudir ];then
    mount --bind /data/local/config/cpudir/ /sys/devices/system/cpu/
  else
    hook_cpu_default
  fi
}

function hook_cpu_default() {
  if [[ -e /sys/devices/system/cpu/cpu0/cpufreq && -e /sys/devices/system/cpu/cpu0/cpuidle ]];then
    return
  fi
  if [[ -d /sys_deivces_system_cpu && $CPUSET_CPUS ]];then
    [[ ! -d /cpu/ ]] && mkdir /cpu/
    chmod 755 /cpu/
    cp -a /sys/devices/system/cpu/possible /cpu/possible
    cp -a /sys/devices/system/cpu/present /cpu/present
    cp -a /sys/devices/system/cpu/present /cpu/online
    mount /cpu/ /sys/devices/system/cpu/

    cpu_index_start=${CPUSET_CPUS%-*}
    cpu_index_offset=$(($(cat /proc/cpuinfo | grep -w processor | wc -l)-1))

    if [[ ${cpu_index_offset} -le 7 ]];then
      echo 7 >/cpu/kernel_max
    else
      echo ${cpu_index_offset} >/cpu/kernel_max
    fi

    chmod 444 /cpu/possible /cpu/present /cpu/online /cpu/kernel_max

    mkdir -p /cpu/cpufreq/policy0 /cpu/cpufreq/cpuidle
    chmod 755 /cpu/cpufreq /cpu/cpufreq/policy0 /cpu/cpufreq/cpuidle

    echo "$(seq 0 ${cpu_index_offset}|tr '\n' ' ')" >/cpu/cpufreq/policy0/affected_cpus
    echo '2400000' >/cpu/cpufreq/policy0/cpuinfo_max_freq
    echo '2400000' >/cpu/cpufreq/policy0/cpuinfo_cur_freq
    echo '1200000' >/cpu/cpufreq/policy0/cpuinfo_min_freq
    echo '0' >/cpu/cpufreq/policy0/cpuinfo_transition_latency
    cat /cpu/cpufreq/policy0/affected_cpus >/cpu/cpufreq/policy0/related_cpus
    echo '1200000 1500000 1800000 2100000 2400000' >/cpu/cpufreq/policy0/scaling_available_frequencies
    echo 'ondemand userspace powersave performance' >/cpu/cpufreq/policy0/scaling_available_governors
    cat /cpu/cpufreq/policy0/cpuinfo_cur_freq >/cpu/cpufreq/policy0/scaling_cur_freq
    echo 'msm' >/cpu/cpufreq/policy0/scaling_driver
    echo 'performance' >/cpu/cpufreq/policy0/scaling_governor
    cat /cpu/cpufreq/policy0/cpuinfo_max_freq >/cpu/cpufreq/policy0/scaling_max_freq
    cat /cpu/cpufreq/policy0/cpuinfo_min_freq >/cpu/cpufreq/policy0/scaling_min_freq
    echo '<unsupported>' >/cpu/cpufreq/policy0/scaling_setspeed

    chmod 444 /cpu/cpufreq/policy0/*
    chmod 400 /cpu/cpufreq/policy0/cpuinfo_cur_freq
    chmod 644 /cpu/cpufreq/policy0/scaling_governor /cpu/cpufreq/policy0/scaling_setspeed
    chmod 660 /cpu/cpufreq/policy0/scaling_max_freq /cpu/cpufreq/policy0/scaling_min_freq
    chown system:system /cpu/cpufreq/policy0/scaling_max_freq /cpu/cpufreq/policy0/scaling_min_freq

    mkdir -p /cpu/cpufreq/cpuidle/driver /cpu/cpufreq/cpuidle/state0 /cpu/cpufreq/cpuidle/state1
    chmod 755 /cpu/cpufreq/cpuidle/*

    echo 'msm_idle' >/cpu/cpufreq/cpuidle/driver/name
    chmod 444 /cpu/cpufreq/cpuidle/driver/name

    echo 'wfi' >/cpu/cpufreq/cpuidle/state0/desc
    echo '0' >/cpu/cpufreq/cpuidle/state0/disable
    echo '1' >/cpu/cpufreq/cpuidle/state0/latency
    echo 'C0\n' >/cpu/cpufreq/cpuidle/state0/name
    echo '0' >/cpu/cpufreq/cpuidle/state0/power
    echo "$((RANDOM*4+11111))" >/cpu/cpufreq/cpuidle/state0/usage
    echo "$(($(cat /cpu/cpufreq/cpuidle/state0/usage)*666))" >/cpu/cpufreq/cpuidle/state0/time
    chmod 444 /cpu/cpufreq/cpuidle/state0/*
    chmod 644 /cpu/cpufreq/cpuidle/state0/disable

    echo 'pc' >/cpu/cpufreq/cpuidle/state1/desc
    echo '0' >/cpu/cpufreq/cpuidle/state1/disable
    echo '160' >/cpu/cpufreq/cpuidle/state1/latency
    echo 'C1\n' >/cpu/cpufreq/cpuidle/state1/name
    echo '0' >/cpu/cpufreq/cpuidle/state1/power
    echo "$((RANDOM+1111))" >/cpu/cpufreq/cpuidle/state1/usage
    echo "$(($(cat /cpu/cpufreq/cpuidle/state1/usage)*22222))" >/cpu/cpufreq/cpuidle/state1/time
    chmod 444 /cpu/cpufreq/cpuidle/state1/*
    chmod 644 /cpu/cpufreq/cpuidle/state1/disable

    for i in $(seq 0 ${cpu_index_offset})
    do
      mkdir -p /cpu/cpu$i
      chmod 755 /cpu/cpu$i
      for j in $(find /sys_deivces_system_cpu/cpu$((cpu_index_start+i)) -mindepth 1 -maxdepth 1 );do
        if [[ -d $j ]];then
          dir_name=$(basename $j)
          mkdir -p /cpu/cpu$i/$dir_name
          mount --bind /sys_deivces_system_cpu/cpu$((cpu_index_start+i))/$dir_name /sys/devices/system/cpu/cpu$i/$dir_name
        elif [[ -f $j ]];then
          file_name=$(basename $j)
          touch /cpu/cpu$i/$file_name
          mount --bind /sys_deivces_system_cpu/cpu$((cpu_index_start+i))/$file_name /sys/devices/system/cpu/cpu$i/$file_name
        elif [[ -L $j ]];then
          link_name=$(basename $j)
          cp -af /sys_deivces_system_cpu/cpu$((cpu_index_start+i))/$link_name /sys/devices/system/cpu/cpu$i/$link_name
        fi
      done
      mkdir -p /cpu/cpu$i/cpufreq /cpu/cpu$i/cpuidle
      mount --bind /cpu/cpufreq/policy0 /sys/devices/system/cpu/cpu$i/cpufreq
      mount --bind /cpu/cpufreq/cpuidle /sys/devices/system/cpu/cpu$i/cpuidle
    done

    for i in `find /sys_deivces_system_cpu -type d -mindepth 1 -maxdepth 1 |grep -vE "cpu[0-9]+|cpufreq"`;do
      dir_name=`basename $i`
      [[ ! -d /cpu/$dir_name ]] && mkdir -p /cpu/$dir_name
      mount --bind /sys_deivces_system_cpu/$dir_name     /sys/devices/system/cpu/$dir_name
    done;

    for i in `find /sys_deivces_system_cpu -type f -maxdepth 1 | grep -v present | grep -v possible | grep -v online | grep -v kernel_max`;do
      file_name=`basename $i`
      touch /cpu/$file_name
      mount --bind /sys_deivces_system_cpu/$file_name /sys/devices/system/cpu/$file_name
    done;
  fi
}

function create_hook_thermal() {
  # mount /sys/devices/virtual/thermal  /thermal
  local i=$1
  mkdir -p /thermal/thermal_zone$i
  echo 'backward_compatible' > /thermal/thermal_zone$i/available_policies
  echo '0' > /thermal/thermal_zone$i/cdev0_trip_point
  echo '0' > /thermal/thermal_zone$i/cdev0_weight
  echo '' > /thermal/thermal_zone$i/integral_cutoff
  echo '' > /thermal/thermal_zone$i/k_d
  echo '' > /thermal/thermal_zone$i/k_i
  echo '' > /thermal/thermal_zone$i/k_po
  echo '' > /thermal/thermal_zone$i/k_pu
  echo 'disabled' > /thermal/thermal_zone$i/mode
  echo ' ' > /thermal/thermal_zone$i/offset
  echo '0' > /thermal/thermal_zone$i/passive
  echo 'backward_compatible' > /thermal/thermal_zone$i/policy
  echo '' > /thermal/thermal_zone$i/slope
  echo '' > /thermal/thermal_zone$i/sustainable_power
  echo '26000' > /thermal/thermal_zone$i/temp
  echo '60000' > /thermal/thermal_zone$i/trip_point_0_temp
  echo 'active' > /thermal/thermal_zone$i/trip_point_0_type
  echo 'mtktsbattery' > /thermal/thermal_zone$i/type
  echo '' > /thermal/thermal_zone$i/uevent
  mkdir -p /thermal/thermal_zone$i/power
  echo '' > /thermal/thermal_zone$i/power/autosuspend_delay_ms
  echo 'auto' > /thermal/thermal_zone$i/power/control
  echo '0' > /thermal/thermal_zone$i/power/runtime_active_time
  echo 'unsupported' > /thermal/thermal_zone$i/power/runtime_status
  echo '0' > /thermal/thermal_zone$i/power/runtime_suspended_time
  ln -s ../../../../class/thermal  /thermal/thermal_zone$i/subsystem

  chmod -R 644 /thermal/thermal_zone$i/*
  chmod -R 755 /thermal/thermal_zone$i/power
  chown -R root:root /thermal/thermal_zone$i
}

function create_hook_thermal_link() {
  # mount /sys/devices/virtual/thermal  /thermal_link
  local i=$1
  mkdir -p /thermal_link
  ln -s ../../devices/virtual/thermal/thermal_zone$i  /thermal_link
}

function hook_thermal() {
  for j in $(seq 0 16);do
    create_hook_thermal $j
    create_hook_thermal_link $j
  done

  umount /sys/devices/virtual/thermal
  mount --bind /thermal /sys/devices/virtual/thermal

  umount /sys/class/thermal
  mount --bind /thermal_link /sys/class/thermal
}

function create_power_supply() {
  mkdir -p /power_supply/battery
  echo "Battery" > /power_supply/battery/type
  echo '80' > /power_supply/battery/capacity
  echo 'Good' > /power_supply/battery/health
  echo '1' > /power_supply/battery/present
  echo 'Not charging' > /power_supply/battery/status
  echo 'Li-poly' > /power_supply/battery/technology
  echo '260' > /power_supply/battery/temp
  echo '4356000' > /power_supply/battery/voltage_now
  echo "3945000" > /power_supply/battery/charge_counter
  echo "4400000" > /power_supply/battery/voltage_max
  echo "3000000" > /power_supply/battery/current_max

  chown -R root:root /power_supply
  chmod -R 755 /power_supply
  chmod 644 /power_supply/battery/*
  chmod 644 /power_supply/usb/*
}

function hook_power_supply() {
  create_power_supply
  umount /sys/class/power_supply
  mount --bind /power_supply /sys/class/power_supply
}

function hook_user_define() {
  if [ -f /data/local/mnt/mnt.cfg ];then
    cat /data/local/mnt/mnt.cfg | while read line
    do
      src_file=${line% *}
      dest_file=${line#* }
      umount ${src_file}
      mount --bind ${dest_file} ${src_file}
    done
  fi
}

function hook_disk_size() {
  rm -rf /block/
  cp -ra /sys/block/ /block/
  rm -rf /block/sd*
  mkdir -p /block/mmcblk0/
  local data_line=`df data | grep -v Filesystem`
  local data_size=`echo $data_line | cut -d ' ' -f 2`
  local block_num=`expr $data_size \* 2`
  echo $block_num > /block/mmcblk0/size
  chmod 444 /block/mmcblk0/size
  mount --bind /block/ /sys/block/
}

function hook_kernel_version() {
  if [ -f /data/local/config/version ];then
    chmod 444 /data/local/config/version
    umount /proc/version
    mount --bind /data/local/config/version /proc/version
  fi
}

build_hardware=hi3660

hook_ro_hardware() {
  local system_64_lib_path="/system/vendor/lib64/hw"
  local system_32_lib_path="/system/vendor/lib/hw"
  if [[ -f /data/local.prop ]];then
    local ro_hardware="$(grep '^ro\.hardware=.*' /data/local.prop|cut -d= -f2|tail -n 1)"
    if [[ -n "${ro_hardware}" && -n "${build_hardware}" && "${ro_hardware}" != "${build_hardware}" ]];then

      ln -s "${system_64_lib_path}/camera.${build_hardware}.so" "${system_64_lib_path}/camera.${ro_hardware}.so"
      ln -s "${system_32_lib_path}/camera.${build_hardware}.so" "${system_32_lib_path}/camera.${ro_hardware}.so"

      ln -s "${system_64_lib_path}/sensors.${build_hardware}.so" "${system_64_lib_path}/sensors.${ro_hardware}.so"
      ln -s "${system_32_lib_path}/sensors.${build_hardware}.so" "${system_32_lib_path}/sensors.${ro_hardware}.so"

      ln -s "${system_64_lib_path}/audio.primary.${build_hardware}.so" "${system_64_lib_path}/audio.primary.${ro_hardware}.so"
      ln -s "${system_32_lib_path}/audio.primary.${build_hardware}.so" "${system_32_lib_path}/audio.primary.${ro_hardware}.so"
      
      for i in $(ls ${system_64_lib_path}|grep -E '^(camera|sensors|audio\.primary)\..+\.so'|grep -Ev "default|goldfish|${ro_hardware}|${build_hardware}");do
        [[ -n "$i" ]] && echo "rm -f ${system_64_lib_path}/$i" && rm -f ${system_64_lib_path}/$i
      done
      
      for i in $(ls ${system_32_lib_path}|grep -E '^(camera|sensors|audio\.primary)\..+\.so'|grep -Ev "default|goldfish|${ro_hardware}|${build_hardware}");do
        [[ -n "$i" ]] && echo "rm -f ${system_32_lib_path}/$i" && rm -f ${system_32_lib_path}/$i
      done
      
    fi
  fi
}

hook_net() {
  ip_addr=$(ip addr show eth0 | grep -w 'inet' | cut -d' ' -f6 | cut -d'/' -f1)
  prefix_length=$(ip addr show eth0 | grep -w 'inet' | cut -d' ' -f6 | cut -d'/' -f2)
  gateway_ip=$(ip route show |grep -w 'default via'|cut -d' ' -f3)
  ifconfig eth0 down
  ip link set eth0 name wlan0
  ifconfig wlan0 up
  ifconfig wlan0 ${ip_addr}/${prefix_length}
  ip route add default via ${gateway_ip} dev wlan0 proto static

  # config IPv6 addr and route if support
  if [ -n "${PHONE_IPV6_CIDR}" ] && [ -n "${BRIDGE_GATEWAY_IPV6_CIDR}" ]; then
    ip -6 addr flush dev wlan0
    ip -6 addr add ${PHONE_IPV6_CIDR} dev wlan0
    ip -6 route add default via ${BRIDGE_GATEWAY_IPV6_CIDR} dev wlan0
  fi
}

function main() {
  copy_cpuinfo_for_exagear
  hook_kernel_version
  hook_cpu_features
  hook_cpu
  hook_power_supply
  hook_thermal
  hook_user_define
  hook_disk_size
  hook_ro_hardware
  hook_net
}

main
