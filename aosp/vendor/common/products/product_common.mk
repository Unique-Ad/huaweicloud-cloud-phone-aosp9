#
# Copyright (c) Huawei Technologies Co., Ltd. 2023-2023. All rights reserved.
# Description: product common config
#

PRODUCT_PACKAGES += \
    kauditd \
    hwcomposer.huawei \
    gralloc.$(BUILD_HARDWARE) \
    sensors.$(BUILD_HARDWARE) \
    camera.$(BUILD_HARDWARE) \
    gps.$(BUILD_HARDWARE) \
    vibrator.$(BUILD_HARDWARE) \
    android.hardware.audio@2.0-impl \
    android.hardware.audio.effect@2.0-impl \
    android.hardware.bluetooth@1.0-impl \
    android.hardware.camera.provider@2.4-impl \
    android.hardware.configstore@1.1 \
    android.hardware.configstore@1.1-service \
    android.hardware.gnss@1.0-impl \
    android.hardware.graphics.composer@2.1-impl \
    android.hardware.graphics.composer@2.1-service \
    android.hardware.graphics.mapper@2.0-impl-2.1 \
    android.hardware.graphics.allocator@2.0-impl \
    android.hardware.graphics.allocator@2.0-service \
    android.hardware.sensors@1.0-impl \
    android.hardware.vibrator@1.0-impl \
    android.hardware.wifi@1.0-service \
    android.hardware.nidec@1.0-impl \
    audio.primary.hi3660 \
    libGLES_mesa \
    vulkan.radv \
    libGLESv1_CM_slow_binding \
    libGLESv2_slow_binding \
    libGLESv3_slow_binding \
    uinput \
    curl \
    Dialer \
    libpsturbo \
    psTurboClientExample \
    hwcap \
    libbt-vendor \
    libwifi-hal-cph \
    wpa_supplicant \
    CphNetworkLocation \
    ni_rsrc_mon \
    ni_rsrc_list

PRODUCT_PROPERTY_OVERRIDES += \
    persist.sys.language=zh \
    persist.sys.country=CN \
    persist.sys.timezone=Asia/Shanghai \
    ro.board.platform=$(BUILD_HARDWARE) \
    ro.hardware=$(BUILD_HARDWARE) \
    ro.hardware.gralloc=$(BUILD_HARDWARE) \
    ro.hardware.hwcomposer=huawei \
    ro.hardware.gpurenderer=Mali-G76 \
    ro.hardware.vulkan.level = 1 \
    ro.hardware.vulkan.version = 4198400 \
    ro.hardware.vulkan = radv \
    ro.build.version.release=9 \
    ro.build.user=test \
    ro.build.product=SIP \
    ro.product.board=SIP \
    ro.product.device=HWSIP \
    ro.product.model=SIP-BX00 \
    ro.product.name=SIP-BX00 \
    ro.product.platform = radv \
    ro.baseband=unknown \
    ro.opengles.version = 196610 \
    ro.sf.lcd_density=320 \
    ro.telephony.default_network=22 \
    dalvik.vm.heapstartsize=16m \
    gsm.version.baseband=21C20B526S000C000 \
    wifi.interface=wlan0 \
    debug.sf.nobootanimation=1 \
    mesa.gallium.thread=false

PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.software.app_widgets.xml:system/etc/permissions/android.software.app_widgets.xml \
    frameworks/native/data/etc/android.hardware.opengles.aep.xml:system/etc/permissions/android.hardware.opengles.aep.xml \
    frameworks/native/data/etc/android.software.backup.xml:system/etc/permissions/android.software.backup.xml \
    frameworks/native/data/etc/android.hardware.bluetooth.xml:system/etc/permissions/android.hardware.bluetooth.xml \
    frameworks/native/data/etc/android.hardware.bluetooth_le.xml:system/etc/permissions/android.hardware.bluetooth_le.xml \
    frameworks/native/data/etc/android.hardware.usb.accessory.xml:system/etc/permissions/android.hardware.usb.accessory.xml \
    frameworks/native/data/etc/android.hardware.wifi.xml:system/etc/permissions/android.hardware.wifi.xml \
    frameworks/native/data/etc/android.hardware.vulkan.level-1.xml:system/etc/permissions/android.hardware.vulkan.level.xml \
    frameworks/native/data/etc/android.hardware.vulkan.version-1_1.xml:system/etc/permissions/android.hardware.vulkan.version.xml \
    vendor/common/android/etc/cph_perf_name_list.xml:system/etc/cph_perf_name_list.xml \
    vendor/common/android/etc/init.wifi.rc:root/init.wifi.rc \
    vendor/common/android/etc/InstallBlacklist:system/etc/InstallBlacklist \
    vendor/common/android/etc/handheld_core_hardware.xml:system/etc/permissions/handheld_core_hardware.xml \
    vendor/common/android/etc/packages_priv.xml:system/etc/packages_priv.xml \
    vendor/common/android/etc/wpa_supplicant.conf:system/etc/wifi/wpa_supplicant.conf \
    vendor/common/android/scripts/buildOverlayfs.sh:system/bin/buildOverlayfs.sh \
    vendor/common/android/scripts/config_phone.sh:system/bin/config_phone.sh \
    vendor/common/android/scripts/configBuildDate.sh:system/bin/configBuildDate.sh \
    vendor/common/android/scripts/cph_logger.sh:system/bin/cph_logger.sh \
    vendor/common/android/scripts/hook.sh:root/hook.sh \
    vendor/common/android/media/media_profiles.xml:system/etc/media_profiles.xml \
    vendor/common/android/media/media_codecs.xml:system/etc/media_codecs.xml \
    vendor/common/android/media/media_codecs_google_audio.xml:system/etc/media_codecs_google_audio.xml \
    vendor/common/android/media/media_codecs_google_telephony.xml:system/etc/media_codecs_google_telephony.xml \
    vendor/common/android/media/media_codecs_google_tv.xml:system/etc/media_codecs_google_tv.xml \
    vendor/common/android/media/media_codecs_google_video.xml:system/etc/media_codecs_google_video.xml \
    vendor/common/android/media/media_codecs_hisi_video.xml:system/etc/media_codecs_hisi_video.xml \

DEVICE_PACKAGE_OVERLAYS += \
    vendor/common/android/overlay
