#!/bin/bash

read_param() {
    [[ "${1}" == "--help" || "${1}" == "-h" ]] && print_usage && exit 0
}

print_usage() {
    cat <<EOF

Usage:
    bash envbuild.sh  初始化环境

EOF
}

python_config() {
    echo "install python module"
    pip3 install sdk-obs-python --trusted-host pypi.org
    echo "pthnon配置完成"
}


apt_install() {
    echo "install apt-get module"
    apt-get update
    apt-get -y install openjdk-8-jdk
    apt -y install git-core gnupg flex bison gperf build-essential zip curl zlib1g-dev gcc-multilib g++-multilib libc6-dev-i386 lib32ncurses5-dev x11proto-core-dev libx11-dev lib32z-dev ccache libgl1-mesa-dev libxml2-utils xsltproc unzip pkg-config autoconf automake libtool dos2unix ntpdate docker.io libncurses5 libncurses5-dev libncursesw5
    echo "组件下载完成"
}

config_env() {
    echo "make config environment"
    echo 'export LC_ALL=C'>>/root/.bashrc
    sed -i 's/SSLv3, TLSv1, TLSv1.1, RC4, DES, MD5withRSA/SSLv3, RC4, DES, MD5withRSA/g' /etc/java-8-openjdk/security/java.security
    sed -i '8a\
LC_ALL=C
    ' /etc/crontab
    rm -f /usr/bin/python
    ln -s /usr/bin/python2 /usr/bin/python
    echo "环境配置完成"
}

repo_env() {
    sudo apt-get install repo
    mkdir -p ~/bin
    export PATH="${HOME}/bin:${PATH}"
    curl https://storage.googleapis.com/git-repo-downloads/repo > ~/bin/repo
    chmod a+rx ~/bin/repo
    echo "repo配置完成"
}

main() {
    read_param "$@"
    echo "----------------------------"
    apt_install
    echo "----------------------------"
    python_config
    echo "----------------------------"
    config_env
    echo "----------------------------"
    repo_env
}

main "$@"


