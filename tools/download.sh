make_code_dir() {
    echo "make code Dir"
    mkdir -p /data/workspace/aosp9/gitee
    mkdir -p /data/workspace/aosp9/.build_config
    mkdir -p /data/workspace/aosp9/prebuild
    mkdir -p /data/workspace/aosp9/aosp
    echo "完成"
}

download_project_code() {
    echo "download aosp project code"
    python3 download.py
    echo "完成"
}

download_opensource_aosp9_code() {
    echo "download opensource code"
    cd /data/workspace/aosp9/aosp/
    export REPO_URL=https://gerrit-googlesource.proxy.ustclug.org/git-repo
    export PATH="${HOME}/bin:${PATH}"
    repo init -u git://mirrors.ustc.edu.cn/aosp/platform/manifest -b android-security-9.0.0_r76
    repo sync
    rm -rf .repo
    git init
    git add .
    git commit -m "init"
    echo 'out/*
frameworks/opt/inputconnectioncommon
packages/services/NetworkRecommendation'>> '.gitignore'
    git add .gitignore
    git commit -m "add gitignore"
}

main() {
    echo "----------------------------"
    make_code_dir
    echo "----------------------------"
    download_project_code
    echo "----------------------------"
    download_opensource_aosp9_code
}

main
