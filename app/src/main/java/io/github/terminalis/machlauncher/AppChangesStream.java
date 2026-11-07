package io.github.terminalis.machlauncher;

import android.content.pm.LauncherApps;
import android.os.UserHandle;

import java.io.Serializable;
import java.util.Map;
import java.util.function.Consumer;

public class AppChangesStream extends LauncherApps.Callback {
    private final LauncherApps launcherApps;
    private final InstalledApps installedApps;
    private Consumer<Map<String, Object>> events;

    public AppChangesStream(LauncherApps launcherApps, InstalledApps installedApps) {
        this.launcherApps = launcherApps;
        this.installedApps = installedApps;
    }

    public void start(Consumer<Map<String, Object>> events) {
        this.events = events;
        launcherApps.registerCallback(this);
    }

    public void stop() {
        launcherApps.unregisterCallback(this);
    }

    @Override
    public void onPackageRemoved(String packageName, UserHandle user) {
        events.accept(Map.of("action", "PACKAGE_REMOVED", "packageName", packageName));
    }

    @Override
    public void onPackageAdded(String packageName, UserHandle user) {
        onPackageChanged(packageName, user);
    }

    @Override
    public void onPackageChanged(String packageName, UserHandle user) {
        Map<String, Serializable> app = installedApps.getApplication(packageName);
        if (app != null) {
            events.accept(Map.of("action", "PACKAGE_CHANGED", "packageName", packageName, "app", app));
        }
    }

    @Override
    public void onPackagesAvailable(String[] packageNames, UserHandle user, boolean replacing) {
    }

    @Override
    public void onPackagesUnavailable(String[] packageNames, UserHandle user, boolean replacing) {
    }
}
