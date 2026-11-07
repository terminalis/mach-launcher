package io.github.terminalis.machlauncher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.view.accessibility.AccessibilityEvent;

public class HomeButtonService extends AccessibilityService {
    private static HomeButtonService running;
    private String systemHome;

    static void turnOff() {
        HomeButtonService service = running;
        running = null;
        if (service != null) {
            service.disableSelf();
        }
    }

    @Override
    protected void onServiceConnected() {
        running = this;
        ResolveInfo home = getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
        systemHome = home == null ? null : home.activityInfo.packageName;
        openMach();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (running == this && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && isSystemHome(event.getPackageName())) {
            openMach();
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (running == this) {
            running = null;
        }
        return super.onUnbind(intent);
    }

    private boolean isSystemHome(CharSequence packageName) {
        return packageName != null && !getPackageName().contentEquals(packageName) && packageName.toString().equals(systemHome);
    }

    private void openMach() {
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
