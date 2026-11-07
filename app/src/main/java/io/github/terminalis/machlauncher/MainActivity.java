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

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;

import androidx.core.content.FileProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    static final String PERMISSION_READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS";

    InstalledApps installedApps;
    TvPrograms tvPrograms;
    ContentImages contentImages;
    AppChangesStream appChanges;
    private HomeScreen home;
    private boolean resumed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        installedApps = new InstalledApps(getPackageManager());
        tvPrograms = new TvPrograms(this);
        contentImages = new ContentImages(getContentResolver());
        appChanges = new AppChangesStream(getSystemService(LauncherApps.class), installedApps);
        home = new HomeScreen(this);
        setContentView(home);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = getSystemService(RoleManager.class);
            if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME) && !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME), 0);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (resumed) {
            home.onBackToFront();
        }
        resumed = true;
    }

    @Override
    protected void onDestroy() {
        home.dispose();
        super.onDestroy();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return home.handleKey(event) || super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
    }

    boolean launchApp(String packageName) {
        return tryStartActivity(installedApps.launchIntent(packageName));
    }

    boolean openSettings() {
        return tryStartActivity(new Intent(Settings.ACTION_SETTINGS));
    }

    boolean openHomeSettings() {
        return tryStartActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
    }

    boolean openAppInfo(String packageName) {
        return tryStartActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(packageName)));
    }

    boolean uninstallApp(String packageName) {
        return tryStartActivity(new Intent(Intent.ACTION_DELETE, packageUri(packageName)));
    }

    boolean checkMediaPermissions() {
        return hasPermission(mediaPermission());
    }

    void requestMediaPermissions() {
        requestPermission(mediaPermission());
    }

    boolean checkTvListingsPermission() {
        return hasPermission(PERMISSION_READ_TV_LISTINGS);
    }

    void requestTvListingsPermission() {
        requestPermission(PERMISSION_READ_TV_LISTINGS);
    }

    private Uri packageUri(String packageName) {
        return Uri.fromParts("package", packageName, null);
    }

    boolean tryStartActivity(Intent intent) {
        try {
            startActivity(intent);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    boolean resetHomeScreen() {
        HomeButtonService.turnOff();
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo tvHome = getPackageManager().resolveActivity(home, 0);
        if (tvHome == null || tvHome.activityInfo.packageName.equals(getPackageName())) {
            return false;
        }
        return tryStartActivity(home.setClassName(tvHome.activityInfo.packageName, tvHome.activityInfo.name));
    }

    boolean installApk(String apkPath) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !getPackageManager().canRequestPackageInstalls()) {
            return false;
        }

        return tryStartActivity(new Intent(Intent.ACTION_VIEW)
                .setDataAndType(
                        FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", new File(apkPath)),
                        "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    boolean requestInstallUnknownAppsPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && tryStartActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri(getPackageName())));
    }

    boolean openInputPicker() {
        Intent intent = new Intent("com.android.tv.action.VIEW_INPUTS");
        PackageManager packageManager = getPackageManager();
        List<String> homeApps = new ArrayList<>();
        for (ResolveInfo home : packageManager.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)) {
            homeApps.add(home.activityInfo.packageName);
        }
        for (ResolveInfo handler : packageManager.queryIntentActivities(intent, 0)) {
            if (!homeApps.contains(handler.activityInfo.packageName)) {
                return tryStartActivity(intent.setClassName(handler.activityInfo.packageName, handler.activityInfo.name));
            }
        }
        return tryStartActivity(intent);
    }

    private String mediaPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return Manifest.permission.READ_MEDIA_IMAGES;
        }
        return Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    boolean hasPermission(String permission) {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission(String permission) {
        requestPermissions(new String[]{permission}, 0);
    }
}
