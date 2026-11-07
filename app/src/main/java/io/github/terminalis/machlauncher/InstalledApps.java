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
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class InstalledApps {
    private final PackageManager packageManager;

    InstalledApps(PackageManager packageManager) {
        this.packageManager = packageManager;
    }

    List<Map<String, Serializable>> getApplications() {
        return new ArrayList<>(packageManager.queryIntentActivities(
                        new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)
                .parallelStream()
                .collect(Collectors.toMap(info -> info.activityInfo.packageName, info -> buildAppMap(info.activityInfo), (first, second) -> first))
                .values());
    }

    Map<String, Serializable> getApplication(String packageName) {
        Intent intent = launchIntent(packageName);
        ActivityInfo activityInfo = intent == null ? null : intent.resolveActivityInfo(packageManager, 0);
        return activityInfo == null ? null : buildAppMap(activityInfo);
    }

    Intent launchIntent(String packageName) {
        return packageManager.getLeanbackLaunchIntentForPackage(packageName);
    }

    byte[] getApplicationBanner(String packageName) {
        Intent intent = launchIntent(packageName);
        try {
            return drawableToByteArray(intent == null ? null : packageManager.getActivityBanner(intent));
        } catch (PackageManager.NameNotFoundException ignored) {
            return new byte[0];
        }
    }

    byte[] getApplicationIcon(String packageName) {
        try {
            return drawableToByteArray(packageManager.getApplicationIcon(packageName));
        } catch (PackageManager.NameNotFoundException ignored) {
            return new byte[0];
        }
    }

    private Map<String, Serializable> buildAppMap(ActivityInfo activityInfo) {
        String version = null;
        try {
            version = packageManager.getPackageInfo(activityInfo.packageName, 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        Map<String, Serializable> appMap = new HashMap<>();
        appMap.put("name", activityInfo.loadLabel(packageManager).toString());
        appMap.put("packageName", activityInfo.packageName);
        appMap.put("version", version == null ? "" : version);
        return appMap;
    }

    private byte[] drawableToByteArray(Drawable drawable) {
        if (drawable == null || drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            return new byte[0];
        }
        Bitmap bitmap;
        if (drawable instanceof BitmapDrawable bitmapDrawable) {
            bitmap = bitmapDrawable.getBitmap();
        } else {
            bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
            drawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
            drawable.draw(new Canvas(bitmap));
        }
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        return stream.toByteArray();
    }
}
