#
# Copyright (c) Huawei Technologies Co., Ltd. 2023-2023. All rights reserved.
# Description: board config
#

ifndef BUILD_HARDWARE
BUILD_HARDWARE = hi3660
endif

PRODUCT_AAPT_CONFIG := normal xhdpi
PRODUCT_AAPT_PREF_CONFIG := xhdpi
PRODUCT_LOCALES := zh_CN en_US

$(call inherit-product, $(SRC_TARGET_DIR)/product/aosp_base.mk)
$(call inherit-product, frameworks/native/build/phone-xhdpi-2048-dalvik-heap.mk)
$(call inherit-product, vendor/common/products/product_common.mk)
$(call inherit-product, vendor/common/products/product_prebuilts.mk)

$(shell (sed -ri "s/(build_hardware=)(.*)/\1$(BUILD_HARDWARE)/" "vendor/common/android/scripts/hook.sh"))
