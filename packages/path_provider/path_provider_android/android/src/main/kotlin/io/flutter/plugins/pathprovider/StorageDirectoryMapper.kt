// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package io.flutter.plugins.pathprovider

import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Environment

/** Helps to map the Dart `StorageDirectory` enum to a Android system constant.  */
internal object StorageDirectoryMapper {
    /**
     * Return a Android Environment constant for a Dart Index.
     *
     * @return The correct Android Environment constant or null, if the index is null.
     * @throws IllegalArgumentException If `dartIndex` is not null but also not matches any known
     * index.
     */
    @Throws(IllegalArgumentException::class)
    fun androidType(dartIndex: Int?): String? {
        return if (dartIndex == null) {
            null
        } else when (dartIndex) {
            0 -> Environment.DIRECTORY_MUSIC
            1 -> Environment.DIRECTORY_PODCASTS
            2 -> Environment.DIRECTORY_RINGTONES
            3 -> Environment.DIRECTORY_ALARMS
            4 -> Environment.DIRECTORY_NOTIFICATIONS
            5 -> Environment.DIRECTORY_PICTURES
            6 -> Environment.DIRECTORY_MOVIES
            7 -> Environment.DIRECTORY_DOWNLOADS
            8 -> Environment.DIRECTORY_DCIM
            9 -> if (VERSION.SDK_INT >= VERSION_CODES.KITKAT) {
                Environment.DIRECTORY_DOCUMENTS
            } else {
                throw IllegalArgumentException("Documents directory is unsupported.")
            }
            else -> throw IllegalArgumentException("Unknown index: $dartIndex")
        }
    }
}