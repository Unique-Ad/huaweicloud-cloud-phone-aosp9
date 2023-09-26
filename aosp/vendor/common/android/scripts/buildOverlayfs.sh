#! /system/bin/sh

PRE_INSTALL_APP_LIST_FILE="/data/appbase/preInstallAppList"
PRE_INSTALL_CONFIG_PKG_NAME="com.cph.config"
OVERLAY_LOCAL_FOLDER="/data/local/appbase"
PRE_INSTALL_CONFIG_FOLDER="${OVERLAY_LOCAL_FOLDER}/com.cph.config/"

#Length of the version folder
TIMESTAMP_LEN=10

######################################################################
#   FUNCTION   : checkTheOldVersion
#   DESCRIPTION: check the phone has oldversion
#   INPUT      : no
#   OUTPUT     : no
######################################################################
checkTheOldVersion()
{
    if [[ ! -d "$PRE_INSTALL_CONFIG_FOLDER" ]]; then
        echo "The old pre_install_config_folder is not found"
        return 0
    fi
    return 1
}
######################################################################
#   FUNCTION   : initPreInstallConfigData
#   DESCRIPTION: process preinstalled files
#   INPUT      : no
#   OUTPUT     : no
######################################################################
initPreInstallConfigData()
{
    checkTheOldVersion
    local check_result=$?
    local oldVersionName=0
    if [[ ${check_result} -eq 1 ]]; then
        #find phone exists version
        for file in $PRE_INSTALL_CONFIG_FOLDER/*
        do
            local temp_num=$(basename ${file})
            if [[ ${#temp_num} -eq ${TIMESTAMP_LEN} ]]; then
                oldVersionName=${temp_num}
                echo "oldVersionName = ${oldVersionName}"
                break
            fi
        done
    fi
    #find the new version
    local newVersionName=0
    if [[ -d "/data/appbase/$PRE_INSTALL_CONFIG_PKG_NAME/" ]]; then
        for file in /data/appbase/$PRE_INSTALL_CONFIG_PKG_NAME/*
        do
            local temp_num=$(basename ${file})
            if [[ ${#temp_num} -ne ${TIMESTAMP_LEN} ]]; then
                continue
            fi
            if [[ ${temp_num} -gt ${newVersionName} ]]; then
                newVersionName=${temp_num}
            fi
        done
    fi

    if [[ -d "${OVERLAY_LOCAL_FOLDER}/$PRE_INSTALL_CONFIG_PKG_NAME/${oldVersionName}/" ]]; then
        if [[ ${oldVersionName} -ne ${newVersionName} ]]; then
            echo "delete the ${oldVersionName} version data"
            umount ${OVERLAY_LOCAL_FOLDER}/${PRE_INSTALL_CONFIG_PKG_NAME}/${oldVersionName}/merged
            rm -rf ${OVERLAY_LOCAL_FOLDER}/$PRE_INSTALL_CONFIG_PKG_NAME/
        fi
    fi
    if [[ ${newVersionName} -eq 0 ]]; then
        echo "The version number is not found"
        return
    fi

    if [[ ! -d ${OVERLAY_LOCAL_FOLDER} ]]; then
        mkdir -p ${OVERLAY_LOCAL_FOLDER}
        chmod 776 ${OVERLAY_LOCAL_FOLDER}
    fi

    mkdir -p ${OVERLAY_LOCAL_FOLDER}/$PRE_INSTALL_CONFIG_PKG_NAME/${newVersionName}/upper
    mkdir -p ${OVERLAY_LOCAL_FOLDER}/$PRE_INSTALL_CONFIG_PKG_NAME/${newVersionName}/work
    mkdir -p ${OVERLAY_LOCAL_FOLDER}/$PRE_INSTALL_CONFIG_PKG_NAME/${newVersionName}/merged
    chmod 777 -R ${OVERLAY_LOCAL_FOLDER}/$PRE_INSTALL_CONFIG_PKG_NAME
    echo "oldVersionName is ${oldVersionName} - newVersionName is ${newVersionName}"
    mount -t overlay appbase -o metacopy=on,lowerdir=/data/appbase/$PRE_INSTALL_CONFIG_PKG_NAME/${newVersionName},upperdir=${OVERLAY_LOCAL_FOLDER}/$PRE_INSTALL_CONFIG_PKG_NAME/${newVersionName}/upper,workdir=${OVERLAY_LOCAL_FOLDER}/$PRE_INSTALL_CONFIG_PKG_NAME/${newVersionName}/work ${OVERLAY_LOCAL_FOLDER}/$PRE_INSTALL_CONFIG_PKG_NAME/${newVersionName}/merged
    #exec copy
    if [[ -d "${OVERLAY_LOCAL_FOLDER}/${PRE_INSTALL_CONFIG_PKG_NAME}/${newVersionName}/merged/data/" ]]; then
        cd "${OVERLAY_LOCAL_FOLDER}/${PRE_INSTALL_CONFIG_PKG_NAME}/${newVersionName}/merged/data/"
        cp -a -f ./* /data/
        cd /
    fi
}

local packages=$(ls ${OVERLAY_LOCAL_FOLDER} | grep -E "^([a-zA-Z][a-zA-Z0-9_]*)+([.][a-zA-Z][a-zA-Z0-9_]*)+$" | grep -v "${PRE_INSTALL_CONFIG_PKG_NAME}")
for packageName in ${packages};do
    echo "packageName = " ${packageName}
    appctrl preinstall ${packageName}
done

if [[ -f "${PRE_INSTALL_APP_LIST_FILE}" ]]; then
    for line in $(cat ${PRE_INSTALL_APP_LIST_FILE})
    do
        if [[ "${PRE_INSTALL_CONFIG_PKG_NAME}" == "${line}" ]]; then
            initPreInstallConfigData
        else
            if [[ ! -d "${OVERLAY_LOCAL_FOLDER}/${line}" ]]; then
                appctrl preinstall ${line}
            fi
        fi
    done
fi

