/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.wifi.scanner;

import android.net.wifi.WifiScanner;
import android.util.Log;

import com.android.server.wifi.WifiNative;

/**
 * KnownBandsChannelHelper that uses band to channel mappings retrieved from wificond.
 * Also supporting updating the channel list from the wificond on demand.
 */
public class WificondChannelHelper extends KnownBandsChannelHelper {
    private static final String TAG = "WificondChannelHelper";

    private final WifiNative mWifiNative;

    public WificondChannelHelper(WifiNative wifiNative) {
        mWifiNative = wifiNative;
        final int[] emptyFreqList = new int[0];
        setBandChannels(emptyFreqList, emptyFreqList, emptyFreqList);
        updateChannels();
    }

    @Override
    public void updateChannels() {
    }
}
