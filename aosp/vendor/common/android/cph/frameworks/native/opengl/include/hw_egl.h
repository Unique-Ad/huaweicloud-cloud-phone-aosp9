/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2023-2023. All rights reserved.
 * Description: egl helper function
 */

#ifndef HW_EGL_VENDOR_H
#define HW_EGL_VENDOR_H

#include <chrono>
#include <string>
#include <cstdint>

void hw_egl_init();
void hw_egl_init_ex();
const GLubyte * hw_egl_get_string_for_current_context(GLenum);
const GLubyte * hw_egl_get_string_for_current_context(GLenum, GLuint);
void hw_egl_print_fps(const char *);
void hw_egl_context_init();
bool hw_egl_create_context(const EGLint *);

std::string hw_get_lib_gles(bool is_gles1);
class GlApiPrinter {
public:
    GlApiPrinter(const std::string&);
    ~GlApiPrinter();
private:
    std::string m_msg;
    std::chrono::time_point<std::chrono::steady_clock> m_start;
};

#endif