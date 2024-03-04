import json
import sys
import os
import requests
import traceback
import subprocess
import urllib3
from configparser import ConfigParser
from obs import GetObjectHeader, PutObjectHeader, ObsClient
from obs import ACL 
from obs import Owner 
from obs import Grant, Permission 
from obs import Grantee, Group

# Server code address
_SCRIPT_PATH = os.path.dirname(os.path.abspath(__file__))

# profile path
_CFG_PATH = os.path.join(_SCRIPT_PATH, 'init.conf')

# Parsing configuration files
config = ConfigParser()
config.read(_CFG_PATH)

iamDomainName = config['account']['iamDomainName']
iamUserName = config['account']['iamUserName']
iamPassword = config['account']['iamPassword']
prebuildBucket = config['bucket']['bucketName']
prebuildServerAddress = config['bucket']['serverAddress']
prebuildRegion = config['bucket']['region']
req_type = config['build']['aosptype']
version = config['build']['version']
git_addr = config['build']['giteeAddress']
second_dot_index = version.index(".", version.index(".") + 1)
version_dic = version[:second_dot_index]

workspace_path = f'/data/workspace/{req_type}'
gitee_path = os.path.join(workspace_path, 'gitee')
prebuild_path = os.path.join(workspace_path, 'prebuild')
build_path = os.path.join(workspace_path, '.build_config')
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

def _ECHO_LOG(message):
    os.system(f'echo {message}')

# Obtain the customer token
def get_token(region, iamDomainName, iamUserName, iamPassword):
    tokenBody = {
        "auth": {
            "identity": {
                "methods": [
                    "password"
                ],
                "password": {
                    "user": {
                        "domain": {
                            "name": iamDomainName
                        },
                        "name": iamUserName,
                        "password": iamPassword
                    }
                }
            },
            "scope": {
                "project": {
                    "name": region
                }
            }
        }
    }
    url = 'https://iam.' + region + '.myhuaweicloud.com/v3/auth/tokens'
    tokenHeaders = {"Content-Type": "application/json"}
    getTokenResult = requests.post(url, headers=tokenHeaders, data=json.dumps(tokenBody), verify=False)
    return getTokenResult.status_code, getTokenResult

# Obtains a temporary AK/SK for uploading images to a temporary bucket
def getAKSK(token, region):
    akskBody = {
        "auth": {
            "identity": {
                "methods": [
                    "token"
                ],
                "token": {
                    "id": token,
                    "duration_seconds": "900"
                }
            }
        }
    }
    akskUrl = 'https://iam.' + region + '.myhuaweicloud.com/v3.0/OS-CREDENTIAL/securitytokens'
    akskHeaders = {"Content-Type": "application/json"}
    getAKSKResult = requests.post(akskUrl, headers=akskHeaders, data=json.dumps(akskBody), verify=False)
    return json.loads(getAKSKResult.text)['credential']['access'], json.loads(getAKSKResult.text)['credential'][
        'secret'], json.loads(getAKSKResult.text)['credential']['securitytoken']

def downloadFromObs(req_type, downloadPath, version_dic, download_objectKey):
    tokenStatus, getTokenRes = get_token(prebuildRegion, iamDomainName, iamUserName, iamPassword)
    if tokenStatus >= 300:
        return tokenStatus
    token = getTokenRes.headers.get('X-Subject-Token')
    # AK,SK
    AK, SK, securitytoken = getAKSK(token, prebuildRegion)
    # init ObsClient
    init_obsClient = ObsClient(
        access_key_id=AK,
        secret_access_key=SK,
        security_token=securitytoken,
        server=prebuildServerAddress
    )
    headers = GetObjectHeader()
    downloadResp = init_obsClient.getObject(prebuildBucket, download_objectKey, downloadPath, headers=headers)
    init_obsClient.close()
    return downloadResp.status

def deploy_prebuild_files(req_type, workspace_path, version_dic):
    _ECHO_LOG(f'Start download prebuild files ...')
    download_prebuild_path = f'{workspace_path}/CPHOS.tar'
    prebuild_objectKey = f'offline/prebuild/{req_type}/CPHOS.tar'
    downloadRespStatus  = downloadFromObs(req_type, download_prebuild_path, version_dic, prebuild_objectKey)
    if (downloadRespStatus < 300):
        _ECHO_LOG(f'download prebuild file end')
        os.system(f'tar -xvf {download_prebuild_path} -C {workspace_path}')
        os.system(f'rm -f {download_prebuild_path}')
        return True
    else:
        _ECHO_LOG(f'download prebuild files faild!')
        return False

def deploy_build_files(req_type, workspace_path, version_dic):
    _ECHO_LOG(f'Start download build files ...')
    download_build_path = f'{workspace_path}/{version_dic}.tar'
    build_objectKey = f'public/build/{req_type}/{version_dic}.tar'
    downloadRespStatus  = downloadFromObs(req_type, download_build_path, version_dic, build_objectKey)
    if (downloadRespStatus < 300):
        _ECHO_LOG(f'download build file end')
        os.system(f'tar -xvf {download_build_path} -C {workspace_path}')
        os.system(f'rm -f {download_build_path}')
        return True
    else:
        _ECHO_LOG(f'download build files faild!')
        return False

def clone_code(tag, gitee_path, gitee_addr):
    _ECHO_LOG(f'Start cloning customer code from {gitee_addr} ...')
    global _PID_CURR
    if not os.path.exists(gitee_path):
        return False
    cmd = f"git clone --depth=1 -b {tag} {gitee_addr} {gitee_path}"
    res_pull = subprocess.Popen(cmd, shell=True)
    res_pull.wait()
    return True if res_pull.returncode == 0 else False

if __name__ == '__main__':
    res_clone = clone_code(version, gitee_path, git_addr)
    if not res_clone:
        _ECHO_LOG("clone_code failed!")
        sys.exit()
    res_prebuild_deploy = deploy_prebuild_files(req_type, prebuild_path, version_dic)
    if not res_prebuild_deploy:
        sys.exit()
    deploy_build_files(req_type, build_path, version_dic)
