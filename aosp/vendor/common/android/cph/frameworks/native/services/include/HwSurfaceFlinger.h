/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2023-2023. All rights reserved.
 * Description: surfaceflinger helper function
 */

bool hwCheckFps();
int64_t hwGetAppVsync(int64_t);
int64_t hwGetSfVsync(int64_t);
int hwGetTransactionTimeout(int);
nsecs_t hwGetSyncPeriod(nsecs_t);
uint64_t hwGetBufferSyncFlag(uint64_t);