/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.documentsui.util;

import android.os.Build;

/**
 * A utility class for checking Android version.
 */
public class VersionUtils {

    private VersionUtils() {
    }

    /**
     * Returns whether the device is running on Android R or newer.
     */
    public static boolean isAtLeastR() {
        return isAtLeastS() // Keep reference to isAtLeastS() so it's not stripped from test apk
                || Build.VERSION.CODENAME.equals("R")
                || Build.VERSION.SDK_INT >= 30;
    }

    /**
     * Returns whether the device is running on Android S or newer.
     */
    public static boolean isAtLeastS() {
        return Build.VERSION.CODENAME.equals("S")
                || Build.VERSION.SDK_INT >= 31;
    }

    /**
     * Returns whether the device is running on Android T or newer.
     */
    public static boolean isAtLeastT() {
        return Build.VERSION.CODENAME.startsWith("T")
                || Build.VERSION.SDK_INT >= 33;
    }
}
