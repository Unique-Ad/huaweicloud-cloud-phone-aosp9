#
# Copyright (c) Huawei Technologies Co., Ltd. 2022-2023. All rights reserved.
# Description: common makefile for gms
#

PRODUCT_PACKAGES += \
    GoogleServicesFramework \
    PrebuiltGmsCore \
    GoogleLoginService \
    Phonesky \

PRODUCT_COPY_FILES += \
    vendor/common/android/gms/system/etc/permissions/extend.xml:system/etc/permissions/extend.xml \
    vendor/common/android/gms/system/etc/sysconfig/google.xml:system/etc/sysconfig/google.xml \
    $(call find-copy-subdir-files,*,vendor/common/android/gms/Phonesky/split,system/priv-app/Phonesky) \
    $(call find-copy-subdir-files,*,vendor/common/android/gms/PrebuiltGmsCore/split,system/priv-app/PrebuiltGmsCore)
