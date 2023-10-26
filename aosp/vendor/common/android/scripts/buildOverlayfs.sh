#! /system/bin/sh

PRE_INSTALL_APP_LIST_FILE="/data/appbase/preInstallAppList"
PRE_INSTALL_CONFIG_PKG_NAME="com.cph.config"
OVERLAY_LOCAL_FOLDER="/data/local/appbase"
PRE_INSTALL_CONFIG_FOLDER="${OVERLAY_LOCAL_FOLDER}/${PRE_INSTALL_CONFIG_PKG_NAME}"
SHARE_CONFIG_FOLDER="/data/appbase/${PRE_INSTALL_CONFIG_PKG_NAME}"

######################################################################
#   FUNCTION   : presetFile
#   DESCRIPTION: exec mount overlay and copy to phone
#   INPUT      : newVersionName
#   OUTPUT     : no
######################################################################
presetFile()
{
    local newVersionName=$1
    mkdir -p ${PRE_INSTALL_CONFIG_FOLDER}/${newVersionName}/upper
    mkdir -p ${PRE_INSTALL_CONFIG_FOLDER}/${newVersionName}/work
    mkdir -p ${PRE_INSTALL_CONFIG_FOLDER}/${newVersionName}/merged
    chmod 776 -R ${PRE_INSTALL_CONFIG_FOLDER}/
    mount -t overlay appbase -o metacopy=on,lowerdir=${SHARE_CONFIG_FOLDER}/${newVersionName},upperdir=${PRE_INSTALL_CONFIG_FOLDER}/${newVersionName}/upper,workdir=${PRE_INSTALL_CONFIG_FOLDER}/${newVersionName}/work ${PRE_INSTALL_CONFIG_FOLDER}/${newVersionName}/merged
    # exec copy
    if [[ -d "${PRE_INSTALL_CONFIG_FOLDER}/${newVersionName}/merged/data/" ]]; then
        cp -a -f ${PRE_INSTALL_CONFIG_FOLDER}/${newVersionName}/merged/data/* /data/
    fi
}

######################################################################
#   FUNCTION   : initPreConfigData
#   DESCRIPTION: process preinstall files
#   INPUT      : no
#   OUTPUT     : no
######################################################################
initPreConfigData()
{
    if [[ ! -d ${SHARE_CONFIG_FOLDER} ]]; then
        return
    fi
    local newVersionName=$(ls -1 ${SHARE_CONFIG_FOLDER} | sort -n | tail -n 1)
    if [[ -z "${newVersionName}" ]]; then
        return
    fi
    local localVersionName
    if [[ -d "${PRE_INSTALL_CONFIG_FOLDER}" ]]; then
        localVersionName=$(ls -1 ${PRE_INSTALL_CONFIG_FOLDER} | sort -n | tail -n 1)
    fi
    if [[ -z "${localVersionName}" ]]; then
        presetFile ${newVersionName}
    else
        # have no newVersion to update
        if [[ ${localVersionName} -ge ${newVersionName} ]]; then
            echo "have no newVersion to update ${localVersionName}->${newVersionName}"
            return
        fi
        # delete the local old version
        if [[ -d "${PRE_INSTALL_CONFIG_FOLDER}/${localVersionName}/" ]]; then
            echo "delete the ${localVersionName} version data"
            umount ${PRE_INSTALL_CONFIG_FOLDER}/${localVersionName}/merged
            rm -rf ${PRE_INSTALL_CONFIG_FOLDER}/${localVersionName}/
        fi
        presetFile ${newVersionName}
    fi
}

######################################################################
#   FUNCTION   : initPreInstallAppAndData
#   DESCRIPTION: process preinstall app and files
#   INPUT      : no
#   OUTPUT     : no
######################################################################
initPreInstallAppAndData()
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
    if grep -q "^${PRE_INSTALL_CONFIG_PKG_NAME}$" ${PRE_INSTALL_APP_LIST_FILE}; then
        initPreConfigData
    fi

    if [[ -z "${localAppbaseExist}" || -f "/data/system/packages.xml" ]]; then
        echo "the phone is not first boot"
        return
    fi

    for line in $(cat ${PRE_INSTALL_APP_LIST_FILE}); do
        if [[ "${PRE_INSTALL_CONFIG_PKG_NAME}" == "${line}" ]]; then
            continue
        fi
        if [[ ! -d "${OVERLAY_LOCAL_FOLDER}/${line}" ]]; then
            appctrl preinstall ${line}
        fi
    done
}

######################################################################
#   FUNCTION   : initPhoneShareApp
#   DESCRIPTION: mount overlay for the phone already install shareApp
#   INPUT      : no
#   OUTPUT     : no
######################################################################
initPhoneShareApp()
{
    if [[ ! -d ${OVERLAY_LOCAL_FOLDER} ]]; then
        return
    fi
    local packages=$(ls ${OVERLAY_LOCAL_FOLDER} | grep -E "^([a-zA-Z][a-zA-Z0-9_]*)+([.][a-zA-Z][a-zA-Z0-9_]*)+$" | grep -v "${PRE_INSTALL_CONFIG_PKG_NAME}")
    for packageName in ${packages}; do
        echo "packageName = " ${packageName}
        appctrl preinstall ${packageName}
    done
}

initPhoneShareApp
initPreInstallAppAndData
