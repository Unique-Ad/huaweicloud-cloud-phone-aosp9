### [华为云手机开源项目仓主入口](https://gitee.com/HuaweiCloudDeveloper/huaweicloud-cloud-phone)

# huaweicloud-cloudPhone-aosp9

#### 介绍
云手机AOSP9开源代码，用于构建运行在华为云手机服务器的Android9镜像

#### 软件架构
本软件基于AOSP 9.0.0_r76开发，支持修改以下路径中的源码
```
aosp/frameworks
aosp/packages
aosp/vendor
```

#### 使用说明
##### 1. 申请权限
请按照以下格式发送邮件，成功后可通过客户经理获取专属构建服务器地址和密钥
```
收件人：
  cphdeveloper@huawei.com
邮件标题：
  申请AOSP9自定义镜像构建权限
邮件内容：
  公司名称：
  联系人：
  联系电话：
  客户经理：
```

##### 2. 准备开发环境
建议使用Ubuntu 18.04，并安装curl，git，gpg等常用软件，确保开发环境可以联网

##### 3. 修改代码
###### 3.1 fork本仓库，本地修改代码
```
建议：
1. 在aosp/vendor/common/android 目录下放置新增的代码文件或者预编译产物
2. 在aosp/vendor/common/products/product_extra.mk 中声明构建规则
3. 如无特殊需求，不要修改aosp/vendor目录下的其他文件，否则可能会对镜像的基本功能造成影响
```

###### 3.2 提交代码，创建tag或branch
+ （必选）tag/branch即版本号，需满足命名规范，见`7.版本管理`
+ （可选）添加镜像构建账号

如果将代码仓设为私有，请将以下账户添加为只读成员，用于拉取代码和构建镜像
```
cphdeveloper@huawei.com
```
+ （可选）本地编译验证

如需本地验证是否存在编译错误，可拉取原生AOSP代码后，复制本地代码到原生目录，执行以下命令进行验证
```
lunch aosp_arm64-eng
make framework -j
```

##### 4. 配置脚本
修改build.sh，其中SERVER_ADDR和KEY为在`1.申请权限`步骤中获取的专属构建服务器地址和密钥
```
BRANCH_TAG    代码的版本号，例如v1.2.1
KEY           专属构建密钥
IMG_NAME      自定义的镜像产物别名
SERVER_ADDR   专属构建服务器
```

##### 5. 构建镜像
执行build.sh，支持以下参数
+ 全量构建

会清理中间产物，速度较慢，建议生产环境使用。
```
./build.sh
```
+ 增量构建

不清理中间产物，速度较快，建议仅用于调试阶段。
```
./build.sh -i
```
+ 查看构建日志

如果构建失败，可通过日志找到错误，修正后重新触发构建。
```
./build.sh -l
```
构建成功后，返回镜像ID

##### 6. 自定义系统签名
使用公共的系统签名有安全风险，可通过以下步骤制作私有系统签名，加密后上传，构建系统可保证私有的系统签名不会泄露。
###### 6.1 制作私有系统签名

请参考 https://android.googlesource.com/platform/build/+/refs/tags/android-security-9.0.0_r76/target/product/security/README

###### 6.2 获取定位SDK的Key

系统网络定位和地理编码接口通过一个系统App，集成定位SDK开发实现，且和当前系统签名绑定

根据系统签名申请Key，请参考 https://lbs.amap.com/api/android-location-sdk/guide/create-project/get-key/

系统App包名

```
com.cph.networklocation
```

获取安全码SHA1

```
keytool -printcert -file platform.x509.pem
```

获取到Key后，将其保存至文件`amap_key`中构建镜像即可。如果更新了系统签名，更换镜像时需要重置手机才生效。


###### 6.3 打包加密私有系统签名文件
完成以上步骤后，将生成的所有文件放入`security`文件夹，`security`文件夹应包含以下文件

```
.
|-- amap_key
|-- media.pk8
|-- media.x509.pem
|-- platform.pk8
|-- platform.x509.pem
|-- releasekey.pk8
|-- releasekey.x509.pem
|-- shared.pk8
|-- shared.x509.pem
|-- testkey.pk8
|-- testkey.x509.pem
|-- verity_key
|-- veritykey.pk8
`-- veritykey.x509.pem
```
依次执行以下命令完成打包：
```
cd security
tar czvf security.tar.gz ./*
```

下载并导入gpg公钥
```
gpg --keyserver hkps://keyserver.ubuntu.com --recv-keys c5c7a35662a6f8080e27ab464a8e757d9b31879e
```

并设置公钥信任等级为5 = I trust ultimate
```
gpg --edit-key C5C7A35662A6F8080E27AB464A8E757D9B31879E
5
```
ctrl+D 退出gpg设置界面后执行
```
gpg --recipient C5C7A35662A6F8080E27AB464A8E757D9B31879E --output security.tar.gz.gpg --encrypt security.tar.gz
```

###### 6.4 上传私有系统签名文件
将加密后的私有系统签名文件包提交到代码仓的以下路径：
```
/aosp/vendor/common/android/security/security.tar.gz.gpg
```

##### 7. 版本管理

本仓库使用tag进行版本管理。命名规则如下，可在本仓库的标签页查看所有tag，例如

```
v1.0.0
v1.1.0
v1.2.0
```
fork自本仓库的代码也必须创建tag，前两位必须与本仓库的tag保持一致，用来表示其对应关系，第三位可以设置为任意数字，例如
```
v1.2.1
```

也支持使用branch方式替换tag



##### 8. 与原仓库保持同步更新

如果在fork代码后，本仓库的代码又发生了更新，可以使用以下方法同步原仓库的更新。

###### 8.1 进入本地仓库目录

###### 8.2 设置upstream仓库
```
git remote add upstream git@gitee.com:HuaweiCloudDeveloper/huaweicloud-cloud-phone-aosp9.git
```

设置后通过以下命令进行查看
```
git remote -v
```

显示以下两行表示设置成功
```
upstream        git@gitee.com:HuaweiCloudDeveloper/huaweicloud-cloud-phone-aosp9.git (fetch)
upstream        git@gitee.com:HuaweiCloudDeveloper/huaweicloud-cloud-phone-aosp9.git (push)
```

###### 8.3 执行命令`git status`检查本地是否有未提交的修改。如果有，则把你本地的有效修改，先从本地仓库推送到你gitee的仓库。最后再执行一次`git status`检查本地已无未提交的修改。

```
git add -A 或者 git add filename
git commit -m "your note"
git push origin master
git status
```

###### 8.4 抓取原仓库的指定tag
```
git pull upstream tag v1.2.0
```

###### 8.5 切换到master分支
```
git checkout master
```

###### 8.6 合并upstream的master分支
```
git merge upstream/master
```
###### 8.7 如有冲突，本地处理冲突后提交代码

###### 8.8 创建tag
```
git tag -a v1.2.1 -m "update tag v1.2.1"
```

###### 8.9 推送代码
```
git push --tags
```

##### 9. 定制webview：将准备好的webview.apk放在aosp/vendor/common/external/webview/webview.apk目录，文件名字固定为webview.apk

