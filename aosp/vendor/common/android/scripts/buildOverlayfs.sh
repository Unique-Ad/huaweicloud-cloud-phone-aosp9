#! /system/bin/sh

APPBASE_PATH="/data/appbase"
PRE_INSTALL_APP_LIST_FILE="${APPBASE_PATH}/preInstallAppList"
PRE_INSTALL_CONFIG_PKG="com.cph.config"
PRE_INSTALL_CONFIG_PKG_LEVEL1="com.cph.config.level1"
PRE_INSTALL_CONFIG_PKG_LEVEL2="com.cph.config.level2"
OVERLAY_LOCAL_FOLDER="/data/local/appbase"
HIGH_LEVEL_CONFIG_PRESET=0

STORAGE_DE="storage_de"
STORAGE_CE="storage_ce"

######################################################################
#   FUNCTION   : processOverlayDir
#   DESCRIPTION: exec mount overlay
#   INPUT      : configPackageName versionName
#   OUTPUT     : no
######################################################################
processOverlayDir()
{
    if [[ $# -ne 2 ]]; then
        echo "$FUNCNAME param num is wrong, need 2, actual is $#."
    fi
    local configPackageName=$1
    local versionName=$2
    mkdir -p ${OVERLAY_LOCAL_FOLDER}/${configPackageName}/${versionName}/upper
    mkdir -p ${OVERLAY_LOCAL_FOLDER}/${configPackageName}/${versionName}/work
    mkdir -p ${OVERLAY_LOCAL_FOLDER}/${configPackageName}/${versionName}/merged
    chmod 776 -R ${OVERLAY_LOCAL_FOLDER}/${configPackageName}/
    mount -t overlay appbase -o metacopy=on,lowerdir=${APPBASE_PATH}/${configPackageName}/${versionName},upperdir=${OVERLAY_LOCAL_FOLDER}/${configPackageName}/${versionName}/upper,workdir=${OVERLAY_LOCAL_FOLDER}/${configPackageName}/${versionName}/work ${OVERLAY_LOCAL_FOLDER}/${configPackageName}/${versionName}/merged
}


######################################################################
#   FUNCTION   : presetFile
#   DESCRIPTION: exec mount overlay and copy to phone
#   INPUT      : configPackageName versionName
#   OUTPUT     : no
######################################################################
presetFile()
{
    if [[ $# -ne 2 ]]; then
        echo "$FUNCNAME param num is wrong, need 2, actual is $#."
    fi
    local configPackageName=$1
    local versionName=$2
    echo "presetFile ${configPackageName} ${versionName}"
    processOverlayDir ${configPackageName} ${versionName}
    # exec copy
    if [[ -d "${OVERLAY_LOCAL_FOLDER}/${configPackageName}/${versionName}/merged/data/" ]]; then
        cp -a -f ${OVERLAY_LOCAL_FOLDER}/${configPackageName}/${versionName}/merged/data/!(system_ce|misc_ce|vendor_ce|media|data|app) /data/
    fi
}

######################################################################
#   FUNCTION   : configWithoutCeData
#   DESCRIPTION: process preinstall without user CE storage files
#   INPUT      : no
#   OUTPUT     : no
######################################################################
configWithoutCeData()
{
    if [[ $# -ne 1 ]]; then
        echo "$FUNCNAME param num is wrong, need 1, actual is $#."
        return
    fi
    local configPackageName=$1
    if [[ ! -d "${APPBASE_PATH}/${configPackageName}" ]]; then
        echo "This package does not exist in share space."
        return
    fi
    local newVersionName="$(ls -1 ${APPBASE_PATH}/${configPackageName} | sort -n | tail -n 1)"
    if [[ -z "${newVersionName}" ]]; then
        return
    fi
    local localVersionName
    if [[ -d "${OVERLAY_LOCAL_FOLDER}/${configPackageName}" ]]; then
        localVersionName="$(ls -1 ${OVERLAY_LOCAL_FOLDER}/${configPackageName} | sort -n | tail -n 1)"
    fi
    if [[ -z "${localVersionName}" ]]; then
        presetFile ${configPackageName} ${newVersionName}
        HIGH_LEVEL_CONFIG_PRESET=1
    else
        # have no newVersion to update
        if [[ ${localVersionName} -ge ${newVersionName} && ${HIGH_LEVEL_CONFIG_PRESET} -eq 0 ]]; then
            echo "have no newVersion to update ${configPackageName} ${localVersionName}->${newVersionName}"
            # 重启后恢复挂载状态, 避免该包被直接删除
            if [[ ! -d  ${OVERLAY_LOCAL_FOLDER}/${configPackageName}/${localVersionName}/merged/data ]]; then
                processOverlayDir ${configPackageName} ${localVersionName}
            fi
            return
        fi
        HIGH_LEVEL_CONFIG_PRESET=1

        presetFile ${configPackageName} ${newVersionName}
    fi
}

######################################################################
#   FUNCTION   : configUserCeData
#   DESCRIPTION: config User Ce Data(system_ce|misc_ce|vendor_ce|media|data|app) and remove old data
#   INPUT      : no
#   OUTPUT     : no
######################################################################
configUserCeData() {
    if [[ $# -ne 1 ]]; then
        echo "$FUNCNAME param num is wrong, need 1, actual is $#."
        return
    fi
    local configPackageName=$1
 
    local version_num="$(ls ${OVERLAY_LOCAL_FOLDER}/${configPackageName} | wc -l)"
 
    if [[ ${version_num} -gt 1 ]]; then
        # delete the local old version
        local oldVersionName="$(ls -1 ${OVERLAY_LOCAL_FOLDER}/${configPackageName} | sort -n | head -n 1)"
        echo "delete the ${configPackageName} ${oldVersionName} version data"
        umount ${OVERLAY_LOCAL_FOLDER}/${configPackageName}/${oldVersionName}/merged
        if [[ $? -ne 0 ]]; then
            umount -l ${OVERLAY_LOCAL_FOLDER}/${configPackageName}/${oldVersionName}/merged
        fi
        rm -rf ${OVERLAY_LOCAL_FOLDER}/${configPackageName}/${oldVersionName}/
    fi
 
    if [[ ${version_num} -gt 1 || $(getprop ro.first_boot) == 1 ]]; then
        # config user ce data and /app data
        local newVersionName="$(ls -1 ${OVERLAY_LOCAL_FOLDER}/${configPackageName} | sort -n | tail -n 1)"
        if [[ -d "${OVERLAY_LOCAL_FOLDER}/${configPackageName}/${newVersionName}/merged/data/" ]]; then
            for ce_dir in system_ce misc_ce vendor_ce media data app; do
                cp -a -f ${OVERLAY_LOCAL_FOLDER}/${configPackageName}/${newVersionName}/merged/data/${ce_dir} /data/
            done
        fi
    fi
}

######################################################################
#   FUNCTION   : processConfigPackage
#   DESCRIPTION: Power on the phone process configPackage init
#   INPUT      : no
#   OUTPUT     : no
######################################################################
processConfigPackage()
{
    if [[ $# -ne 2 ]]; then
        echo "$FUNCNAME param num is wrong, need 2, actual is $#."
    fi
    local configPackageName=$1
    local configSpace=$2
    if [[ -d "${OVERLAY_LOCAL_FOLDER}/${configPackageName}" ]] || ([[ -f "${PRE_INSTALL_APP_LIST_FILE}" ]] && grep -q "^${configPackageName}$" ${PRE_INSTALL_APP_LIST_FILE}); then
        if [[ ${STORAGE_DE} == ${configSpace} ]]; then
            configWithoutCeData ${configPackageName}
        else
            configUserCeData ${configPackageName}
        fi
    fi
}

######################################################################
#   FUNCTION   : initPreConfig
#   DESCRIPTION: process preinstall app and files
#   INPUT      : no
#   OUTPUT     : no
######################################################################
initPreConfig()
{
    local configSpace=$1
    if [[ ! -d ${OVERLAY_LOCAL_FOLDER} ]]; then
        mkdir -p ${OVERLAY_LOCAL_FOLDER}
        chmod 776 ${OVERLAY_LOCAL_FOLDER}
    fi

    # 配置包按照顺序去执行
    processConfigPackage ${PRE_INSTALL_CONFIG_PKG} ${configSpace}
    processConfigPackage ${PRE_INSTALL_CONFIG_PKG_LEVEL1} ${configSpace}
    processConfigPackage ${PRE_INSTALL_CONFIG_PKG_LEVEL2} ${configSpace}
}

######################################################################
#   FUNCTION   : initPreInstallApp
#   DESCRIPTION: process preinstall app and files
#   INPUT      : no
#   OUTPUT     : no
######################################################################
initPreInstallApp()
{
    if [[ ! -f "${PRE_INSTALL_APP_LIST_FILE}" ]]; then
        return
    fi
    local localAppbaseExist
    if [[ ! -d ${OVERLAY_LOCAL_FOLDER} ]]; then
        mkdir -p ${OVERLAY_LOCAL_FOLDER}
        chmod 776 ${OVERLAY_LOCAL_FOLDER}
        localAppbaseExist=1
    fi

    if [[ ${IS_SUPPORT_FSCRYPT} != true && (-z "${localAppbaseExist}" || -f "/data/system/packages.xml") ]]; then
        echo "the phone is not first boot"
        return
    fi
 
    # the encrypt type should check by prop.
    if [[ ${IS_SUPPORT_FSCRYPT} == true && ! $(getprop ro.first_boot) == 1 ]]; then
        echo "the phone is not first boot"
        return
    fi

    for line in $(cat ${PRE_INSTALL_APP_LIST_FILE}); do
        if [[ "${line}" == "${PRE_INSTALL_CONFIG_PKG}" || "${line}" == "${PRE_INSTALL_CONFIG_PKG_LEVEL1}" || "${line}" == "${PRE_INSTALL_CONFIG_PKG_LEVEL2}" ]]; then
            continue
        fi
        if [[ ! -d "${OVERLAY_LOCAL_FOLDER}/${line}" ]]; then
            appctrl preinstall ${line}
        fi
    done
}

######################################################################
#   FUNCTION   : initPhoneExistSharePackage
#   DESCRIPTION: process the phone already install shareApp and configData
#   INPUT      : no
#   OUTPUT     : no
######################################################################
initPhoneExistSharePackage()
{
    if [[ ! -d ${OVERLAY_LOCAL_FOLDER} ]]; then
        return
    fi
    local packages="$(ls ${OVERLAY_LOCAL_FOLDER} | grep -E "^([a-zA-Z][a-zA-Z0-9_]*)+([.][a-zA-Z][a-zA-Z0-9_]*)+$")"
    for packageName in ${packages}; do
        if [[ "${packageName}" == "${PRE_INSTALL_CONFIG_PKG}" || "${packageName}" == "${PRE_INSTALL_CONFIG_PKG_LEVEL1}" || "${packageName}" == "${PRE_INSTALL_CONFIG_PKG_LEVEL2}" ]]; then
            continue
        fi
        echo "packageName = " ${packageName}
        appctrl preinstall ${packageName}
    done
}

######################################################################
#   FUNCTION   : initShareApp
#   DESCRIPTION:
#   INPUT      : no
#   OUTPUT     : no
######################################################################
initShareApp()
{
    if [[ ${IS_SUPPORT_FSCRYPT} != true && $(getprop sys.boot_completed) != 1 ]]; then
        initPhoneExistSharePackage
        initPreInstallApp
        initPreConfig ${STORAGE_DE}
        initPreConfig ${STORAGE_CE}
    elif [[ ${IS_SUPPORT_FSCRYPT} == true ]]; then
        if [[ $(getprop sys.boot_completed) != 1 ]]; then
            initPreConfig ${STORAGE_DE}
            [[ ! -f "/data/system/packages.xml" ]] && setprop ro.first_boot 1
        else
            initPhoneExistSharePackage
            initPreInstallApp
            initPreConfig ${STORAGE_CE}
            pm scan-fast /data/app
        fi
    fi
    return 0
}

initShareApp
