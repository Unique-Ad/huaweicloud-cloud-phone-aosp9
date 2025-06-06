# Copyright 2013 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
	screenrecord.cpp \
	EglWindow.cpp \
	FrameOutput.cpp \
	TextRenderer.cpp \
	Overlay.cpp \
	Program.cpp

LOCAL_STATIC_LIBRARIES := libscreenrecord_vendor

LOCAL_SHARED_LIBRARIES := \
	libstagefright libmedia libmedia_omx libutils libbinder libstagefright_foundation \
	libjpeg libui libgui libcutils liblog libEGL libGLESv2 libCPHMediaEngine libmediandk

LOCAL_C_INCLUDES := \
	frameworks/av/media/libstagefright \
	frameworks/av/media/libstagefright/include \
	frameworks/native/include/media/openmax \
	external/jpeg

LOCAL_CFLAGS := -Werror -Wall
LOCAL_CFLAGS += -Wno-multichar
#LOCAL_CFLAGS += -UNDEBUG

LOCAL_MODULE_TAGS := optional

LOCAL_MODULE:= screenrecord

include $(BUILD_EXECUTABLE)
