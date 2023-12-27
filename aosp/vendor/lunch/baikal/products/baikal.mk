#
# Copyright (c) Huawei Technologies Co., Ltd. 2022-2023. All rights reserved.
# Description: product makefile for baikal
#

$(call inherit-product, vendor/common/products/arm64/device.mk)
$(call inherit-product, vendor/common/products/common.mk)
#$(call inherit-product, vendor/common/products/cph_common.mk)

PRODUCT_NAME := baikal
PRODUCT_DEVICE := arm64
PRODUCT_BRAND := Huawei
PRODUCT_MODEL := cloudphone
