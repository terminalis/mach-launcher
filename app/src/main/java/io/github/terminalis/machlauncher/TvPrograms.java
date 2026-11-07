/*
 * FLauncher
 * Copyright (C) 2021  Oscar Rojas
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.terminalis.machlauncher;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;
import android.text.TextUtils;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

class TvPrograms {
    private final MainActivity activity;

    TvPrograms(MainActivity activity) {
        this.activity = activity;
    }

    Map<String, List<Map<String, Object>>> getPrograms() {
        Map<String, String> appNames = new HashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        String[] channelProjection = {TvContract.Channels._ID, TvContract.Channels.COLUMN_TYPE};
        try (Cursor channels = activity.getContentResolver().query(TvContract.Channels.CONTENT_URI, channelProjection, null, null, null)) {
            while (channels.moveToNext()) {
                if (TvContract.Channels.TYPE_PREVIEW.equals(channels.getString(1))) {
                    rows.addAll(readPrograms(TvContract.buildPreviewProgramsUriForChannel(channels.getLong(0)), appNames));
                }
            }
        }
        Map<String, List<Map<String, Object>>> programs = new HashMap<>();
        programs.put("rows", rows);
        programs.put("playNext", readPrograms(TvContract.WatchNextPrograms.CONTENT_URI, appNames));
        return programs;
    }

    private List<Map<String, Object>> readPrograms(Uri uri, Map<String, String> appNames) {
        String[] projection = {
            TvContract.PreviewPrograms._ID,
            TvContract.PreviewPrograms.COLUMN_PACKAGE_NAME,
            TvContract.PreviewPrograms.COLUMN_TITLE,
            TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION,
            TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI,
            TvContract.PreviewPrograms.COLUMN_THUMBNAIL_URI,
            TvContract.PreviewPrograms.COLUMN_INTENT_URI,
            TvContract.PreviewPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
            TvContract.PreviewPrograms.COLUMN_DURATION_MILLIS,
        };
        List<Map<String, Object>> programs = new ArrayList<>();
        try (Cursor cursor = activity.getContentResolver().query(uri, projection, null, null, null)) {
            while (cursor.moveToNext()) {
                String packageName = cursor.getString(1);
                String appName = appNames.computeIfAbsent(packageName, this::installedAppName);
                String title = cursor.getString(2);
                String thumbnail = cursor.getString(5);
                String poster = TextUtils.isEmpty(cursor.getString(4)) ? thumbnail : cursor.getString(4);
                String intentUri = cursor.getString(6);
                if (appName == null || TextUtils.isEmpty(title) || TextUtils.isEmpty(poster) || TextUtils.isEmpty(intentUri)) {
                    continue;
                }
                long duration = cursor.getLong(8);
                Map<String, Object> program = new HashMap<>();
                program.put("id", cursor.getLong(0));
                program.put("packageName", packageName);
                program.put("appName", appName);
                program.put("title", title);
                program.put("description", Objects.toString(cursor.getString(3), ""));
                program.put("posterUri", poster);
                if (!TextUtils.isEmpty(thumbnail) && !thumbnail.equals(poster)) {
                    program.put("thumbnailUri", thumbnail);
                }
                program.put("intentUri", intentUri);
                program.put("progressPercent", duration > 0 ? (int) (cursor.getLong(7) * 100 / duration) : 0);
                programs.add(program);
            }
        }
        return programs;
    }

    private String installedAppName(String packageName) {
        try {
            ApplicationInfo info = activity.getPackageManager().getApplicationInfo(packageName, 0);
            return info.enabled ? activity.getPackageManager().getApplicationLabel(info).toString() : null;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    boolean launchProgram(String packageName, String intentUri) {
        try {
            return activity.tryStartActivity(Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME)
                    .setPackage(packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
