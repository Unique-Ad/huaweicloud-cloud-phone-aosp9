#
# Copyright (c) Huawei Technologies Co., Ltd. 2023-2023. All rights reserved.
# Description: product prebuilts config
#

PRODUCT_PACKAGES += \
    libVmiStream \
    libCPHMediaEngine \
    libCPHVideoEngine \
    libCPHTurboVideoEngine \
    libCPHMediaServer \
    libCaptureEngine \
    libVmiOperation \
    libCPHIVmiAudio \
    libCPHVmiAudioRecord \
    libvmiservice \
    libVmiCJSON \
    libVmiMediaCommon \
    libEncodeEngine \
    libVmiDevice \
    audio.primary.$(BUILD_HARDWARE) \
    sipcserver \
    libSipc \
    libavcodec \
    libavformat \
    libavutil \
    libswresample \
    libd310encode \
    libRemoteEncoder \
    libVmiShareMem \
    libevent \
    libLLVM13 \
    libGLES_mesa \
    vulkan.radv \
    libgbm \
    gallium_dri \
    libglapi \
    libdrm \
    libdrm_amdgpu \
    libdrm_radeon \
    libva \
    libva-android \
    libjni_keys \
    libxcoder \
    wget \
    cell.db \
    sensor.db \
    libmCODEC \
    libmCODECNetINT \
    android.hardware.nidec@1.0-impl

PRODUCT_COPY_FILES += \
    vendor/common/android/prebuilts/system/etc/mediaEngine/config.json:system/etc/mediaEngine/config.json \
    vendor/common/android/prebuilts/system/etc/mediaEngine/media_default.json:system/etc/mediaEngine/media_default.json \
    vendor/common/android/prebuilts/system/etc/mediaEngine/media_1_540x960_80_42.json:system/etc/mediaEngine/media_1_540x960_80_42.json \
    vendor/common/android/prebuilts/system/etc/mediaEngine/media_1_720x1280_40_33.json:system/etc/mediaEngine/media_1_720x1280_40_33.json \
    vendor/common/android/prebuilts/system/etc/mediaEngine/media_1_720x1280_40_24.json:system/etc/mediaEngine/media_1_720x1280_40_24.json \
    vendor/common/android/prebuilts/system/etc/mediaEngine/media_1_720x1280_40_31.json:system/etc/mediaEngine/media_1_720x1280_40_31.json \
    vendor/common/android/prebuilts/system/etc/mediaEngine/media_1_1080x1920_20_33.json:system/etc/mediaEngine/media_1_1080x1920_20_33.json \
    vendor/common/android/prebuilts/system/etc/mediaEngine/media_1_1080x1920_20_24.json:system/etc/mediaEngine/media_1_1080x1920_20_24.json \
    vendor/common/android/prebuilts/system/bin/media_logrotate.sh:system/bin/media_logrotate.sh \
    vendor/common/android/prebuilts/system/bin/start_cae.sh:system/bin/start_cae.sh \
    vendor/common/android/prebuilts/system/vendor/etc/init/cae.rc:system/vendor/etc/init/cae.rc \
    vendor/common/android/prebuilts/system/vendor/bin/CloudAppEngine:system/vendor/bin/CloudAppEngine \
    vendor/common/android/prebuilts/system/lib64/libcurl.so:system/lib64/libcurl.so \
    vendor/common/android/prebuilts/system/etc/init/media-engine.rc:system/etc/init/media-engine.rc \
    vendor/common/android/prebuilts/system/vendor/etc/init/sipcserver.rc:system/vendor/etc/init/sipcserver.rc \
    vendor/common/android/prebuilts/system/usr/keylayout/Vendor_0001_Product_0001.kl:system/usr/keylayout/Vendor_0001_Product_0001.kl \
    vendor/common/android/prebuilts/system/usr/keylayout/Vendor_0002_Product_0002.kl:system/usr/keylayout/Vendor_0002_Product_0002.kl \
    vendor/common/android/media/audio_policy.conf:system/etc/audio_policy.conf
