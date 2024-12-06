#pragma once

#include <string>
#include "android-base/logging.h"
#include "adb_trace.h"

#define AUDITLOG(TAG)                 \
    if (VLOG_IS_ON(TAG)) \
        LOG(INFO)

// You must define AUDIT_TRACE_TAG before using this macro.
#define AUDI(...) \
    AUDITLOG(AUDIT_TRACE_TAG) << android::base::StringPrintf(__VA_ARGS__)

void AuditLogger(android::base::LogSeverity level, const char *tag, const char *message);