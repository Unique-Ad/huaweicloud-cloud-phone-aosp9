LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := DfgxBrowser
LOCAL_MODULE_TAGS := optional
LOCAL_CERTIFICATE := PRESIGNED
LOCAL_SRC_FILES := ${LOCAL_MODULE}.apk
LOCAL_MODULE_CLASS := APPS
LOCAL_PRIVILEGED_MODULE := true
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)
LOCAL_OVERRIDES_PACKAGES := Browser2
LOCAL_PREBUILT_JNI_LIBS := lib/arm64-v8a/libaegissec.so \
lib/arm64-v8a/libaeswb.so \
lib/arm64-v8a/libapms_ndk_anr.so \
lib/arm64-v8a/libbase.huawei.so \
lib/arm64-v8a/libchrome_zlib.huawei.so \
lib/arm64-v8a/libcrashpad_handler_trampoline.huawei.so \
lib/arm64-v8a/libffmpeg.huawei.so \
lib/arm64-v8a/libglide-webp.so \
lib/arm64-v8a/libhwwv.so \
lib/arm64-v8a/libicui18n.huawei.so \
lib/arm64-v8a/libicuuc.huawei.so \
lib/arm64-v8a/libml-vadenergy.so \
lib/arm64-v8a/libopus_voicekit.so \
lib/arm64-v8a/libscannative.so \
lib/arm64-v8a/libucs-credential.so \
lib/arm64-v8a/libv8.huawei.so \
lib/arm64-v8a/libwebviewchromium.huawei.so \
lib/arm64-v8a/libwebviewchromium_plat_support.huawei.so \
lib/arm64-v8a/libweibosdkcore.so 
include $(BUILD_PREBUILT)
