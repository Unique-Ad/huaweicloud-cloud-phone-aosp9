LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := DfgxInput
LOCAL_MODULE_TAGS := optional
LOCAL_CERTIFICATE := PRESIGNED
LOCAL_SRC_FILES := ${LOCAL_MODULE}.apk
LOCAL_MODULE_CLASS := APPS
LOCAL_PRIVILEGED_MODULE := true
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)
LOCAL_DEX_PREOPT := false
LOCAL_PREBUILT_JNI_LIBS := lib/arm64-v8a/libexpressive_concepts_model_less_predictor_jni_native.so \
lib/arm64-v8a/libgrammar-checker_jni.so \
lib/arm64-v8a/libintegrated_shared_object.so \
lib/arm64-v8a/libnative_crash_handler_jni.so \
lib/arm64-v8a/libogg_opus_encoder.so \
lib/arm64-v8a/libtensorflow_jni.so
include $(BUILD_PREBUILT)
