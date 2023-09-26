# huaweicloud-cloudPhone-aosp9

#### 介绍
云手机AOSP9开源代码，用于构建运行在华为云手机服务器的Android9镜像

#### 软件架构

本软件基于开源AOSP 9.0.0_r76原生代码开发，包含以下代码路径，您可以修改路径下的源码或者新增代码文件以自定义您的功能
```
aosp/frameworks
aosp/packages
aosp/vendor
```

+ 请将您的新增的代码文件或者预编译产物放在aosp/vendor/common/android目录下，并且在aosp/vendor/common/products/product_extra.mk中声明其构建规则，如无特殊说明，请尽量不要修改aosp/vendor目录下的其他文件，否则可能会对镜像的基本功能造成影响。

#### 使用说明

##### 1. 申请构建权限，请按照以下格式发邮件给`cphdeveloper@huawei.com`
邮件标题：
```
申请AOSP9自定义镜像构建权限
```
邮件内容：
```
公司名称：
联系人：
联系电话：
客户经理：
```
申请成功后，我们会通过邮件发送您的专属构建密钥和服务器地址

##### 2. 准备开发环境
建议使用Ubuntu 18.04，并安装curl，git，gpg等常用软件，确保开发环境可以联网

##### 3. 修改代码
fork本仓库，本地修改代码后，提交到您自己的仓库，然后打TAG或创建Branch
+ TAG/Branch命名规则：以v开头，前两位请与您fork代码的TAG保持一致，构建系统会根据前两位进行匹配。
```
例如，您fork了TAG为v1.2.0的版本，您可以为您要构建的代码打TAG为v1.2.1或者v1.2.2等。
```
如果您的代码仓设为私有，请将以下账户添加为成员，赋予只读权限，用于拉取代码
```
cphdeveloper@huawei.com
```
+ 如果您已经在本地搭建过AOSP的本地编译环境，您在提交代码前可以先将修改过的代码拷贝至您的本地编译环境，检查是否存在语法错误
```
lunch aosp_arm64-eng
make framework -j
```
确保没有编译错误后，再提交代码

##### 4. 配置脚本
构建前请先在本地修改build.sh。其中key和server_addr请咨询华为云手机技术支持人员获取。
```
BRANCH_TAG    您要构建代码的tag/Branch，例如 v1.2.1
KEY           您的专属构建密钥
IMG_NAME      您自定义的镜像产物别名
SERVER_ADDR   您的专属构建服务器
```

##### 5. 触发构建

+ 配置完成后，可执行build.sh触发构建，构建过程在远端的服务器上进行，并且会实时打印构建进度，如果由于您提交的代码问题导致构建失败，请重新提交代码，并更新TAG后重新触发构建，如果构建成功，您将获取到一个镜像ID，请在华为云手机控制台使用该镜像ID更新镜像。支持两种构建方式：
+ 全量构建，会清理中间产物，速度较慢，建议生产环境使用
```
./build.sh
```
+ 增量构建，不清理中间产物，速度较快，建议仅用于调试阶段
```
./build.sh -i
```
+ 查看构建日志，您在使用build.sh执行构建的过程中，可实时打印当前日志。您也可以通过执行下面命令获取当前全量日志打印。
```
./build.sh -l
```

##### 6. 自定义系统签名
构建系统默认使用AOSP9原生代码的系统签名，为确保您的镜像安全，请务必生成您自己的系统签名，然后按照以下步骤提交，构建系统会替换原生系统签名
+ 假设您的系统签名所在路径为 /your/directory/security，请依次执行
```
tar czvf security.tar.gz /your/directory/security
gpg --recipient C5C7A35662A6F8080E27AB464A8E757D9B31879E --output security.tar.gz.gpg --encrypt security.tar.gz
```
+ 将加密后的系统签名提交到代码仓的以下路径
```
/aosp/vendor/common/android/security/security.tar.gz.gpg
```

##### 7. 版本管理
本仓库使用tag进行版本管理，您可以使用tag或branch对您fork的仓库进行管理版本，以下以tag方式为例，branch的方式也是类似的。首先对版本号格式进行说明，例如
```
v1.2.0
```
前两位分别表示大版本和小版本，第三位预留给您使用。由于构建系统需要您传入tag/branch，并且会根据您传入tag/branch的前两位进行匹配，所以请确保您打的tag/branch与所fork代码的tag的前两位保持一致，您可以用第三位表示您自己的版本，例如
```
v1.2.1
```

##### 8. 与原仓库保持同步更新
当您fork代码后，原仓库的代码又发生了更新，您可以使用以下方法同步原仓库的更新，假设您fork后使用master分支

+ Step 1. 进入本地仓库目录

+ Step 2. 把原仓库设置为您的upstream仓库
```
git remote add upstream git@gitee.com:HuaweiCloudDeveloper/huaweicloud-cloud-phone-aosp9.git
```

设置后通过以下命令进行查看
```
git remote -v
```

如果出现以下两行，表示设置成功
```
upstream        git@gitee.com:HuaweiCloudDeveloper/huaweicloud-cloud-phone-aosp9.git (fetch)
upstream        git@gitee.com:HuaweiCloudDeveloper/huaweicloud-cloud-phone-aosp9.git (push)
```

+ Step 3. 执行命令`git status`检查本地是否有未提交的修改。如果有，则把你本地的有效修改，先从本地仓库推送到你gitee的仓库。最后再执行一次`git status`检查本地已无未提交的修改。

```
git add -A 或者 git add filename
git commit -m "your note"
git push origin master
git status
```

+ Step 4. 抓取原仓库的更新
```
git fetch upstream
```

+ Step 5. 切换到master分支
```
git checkout master
```

+ Step 6. 合并upstream的master分支
```
git merge upstream/master
```

+ Step 7. 本地处理冲突后提交代码（如果没有冲突可跳过）

+ Step 8. 打tag
```
git tag -a v1.2.1 -m "update tag v1.2.1"
```

+ Step 9. 推送代码（包含tags）到远端
```
git push --tags
```