/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2023-2023. All rights reserved.
 * Description: jni helper function
 */

enum hwStatsType {
    RX_BYTES = 0,
    RX_PACKETS = 1,
    TX_BYTES = 2,
    TX_PACKETS = 3,
    TCP_RX_PACKETS = 4,
    TCP_TX_PACKETS = 5
};

bool hwNetworkStats();
uint64_t hwGetIfaceStats(const char* iface, hwStatsType type);