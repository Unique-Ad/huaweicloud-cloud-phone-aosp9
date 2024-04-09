/*
 * Copyright (C) 2014 The Android Open Source Project
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */

#include <sys/sysinfo.h>

#include <dirent.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

#include "private/get_cpu_count_from_string.h"
#include "private/ScopedReaddir.h"

int get_nprocs_conf() {
  //use /proc/cpuinfo to get cpu processor number
  int result = 0;
  char buf[512];
  char *line;
  FILE *file = fopen("/proc/cpuinfo", "r");
  if (file) {
      while ((line = fgets(buf, 512, file))) {
          if (strncmp(line, "processor", 9) == 0) {
              ++result;
          }
      }
      fclose(file);
  } else {
      result = 1;
  }
  return result;
}

int get_nprocs() {
  int cpu_count = 1;
  FILE* fp = fopen("/sys/devices/system/cpu/online", "re");
  if (fp != nullptr) {
    char* line = nullptr;
    size_t len = 0;
    if (getline(&line, &len, fp) != -1) {
      cpu_count = GetCpuCountFromString(line);
      free(line);
    }
    fclose(fp);
  }
  return cpu_count;
}

long get_phys_pages() {
  struct sysinfo si;
  sysinfo(&si);
  return (static_cast<int64_t>(si.totalram) * si.mem_unit) / PAGE_SIZE;
}

long get_avphys_pages() {
  struct sysinfo si;
  sysinfo(&si);
  return ((static_cast<int64_t>(si.freeram) + si.bufferram) * si.mem_unit) / PAGE_SIZE;
}
