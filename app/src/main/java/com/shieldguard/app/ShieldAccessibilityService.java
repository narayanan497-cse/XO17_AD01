package com.shieldguard.app;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

public class ShieldAccessibilityService
        extends AccessibilityService {

    private WindowManager windowManager;

    private FrameLayout shieldView;

    private boolean threatActive = false;

    private String currentPackage = "";

    // =========================================================
    // RECEIVER
    // =========================================================

    private final BroadcastReceiver receiver =
            new BroadcastReceiver() {

                @Override
                public void onReceive(
                        Context context,
                        Intent intent
                ) {

                    threatActive =
                            intent.getBooleanExtra(
                                    ShieldBus.EXTRA_ACTIVE,
                                    false
                            );

                    updateOverlay();
                }
            };

    // =========================================================
    // SERVICE CONNECTED
    // =========================================================

    @Override
    protected void onServiceConnected() {

        super.onServiceConnected();

        windowManager =
                (WindowManager)
                        getSystemService(
                                WINDOW_SERVICE
                        );

        IntentFilter filter =
                new IntentFilter(
                        ShieldBus.ACTION_SHIELD
                );

        if (Build.VERSION.SDK_INT >= 33) {

            registerReceiver(
                    receiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
            );

        } else {

            registerReceiver(
                    receiver,
                    filter
            );
        }
    }

    // =========================================================
    // ACCESSIBILITY EVENTS
    // =========================================================

    @Override
    public void onAccessibilityEvent(
            @Nullable AccessibilityEvent event
    ) {

        if (event == null ||
                event.getPackageName() == null) {

            return;
        }

        currentPackage =
                event.getPackageName()
                        .toString();

        updateOverlay();
    }

    @Override
    public void onInterrupt() {

        removeOverlay();
    }

    // =========================================================
    // OVERLAY LOGIC
    // =========================================================

    private void updateOverlay() {

        boolean ownApp =
                getPackageName()
                        .equals(currentPackage);

        boolean systemUi =
                currentPackage.startsWith(
                        "com.android.systemui"
                )
                        ||
                        currentPackage
                                .toLowerCase()
                                .contains("launcher");

        if (threatActive &&
                !ownApp &&
                !systemUi) {

            showOverlay();

        } else {

            removeOverlay();
        }
    }

    // =========================================================
    // SHOW
    // =========================================================

    private void showOverlay() {

        if (shieldView != null ||
                windowManager == null) {

            return;
        }

        shieldView =
                new FrameLayout(this);

        shieldView.setBackgroundColor(
                Color.rgb(
                        7,
                        10,
                        18
                )
        );

        shieldView.setClickable(true);

        shieldView.setFocusable(true);

        LinearLayout center =
                new LinearLayout(this);

        center.setOrientation(
                LinearLayout.VERTICAL
        );

        center.setGravity(
                Gravity.CENTER
        );

        center.setPadding(
                48,
                48,
                48,
                48
        );

        TextView icon =
                new TextView(this);

        icon.setText("🔒");

        icon.setTextSize(46);

        icon.setGravity(
                Gravity.CENTER
        );

        TextView title =
                new TextView(this);

        title.setText(
                "PRIVACY SHIELD ACTIVE"
        );

        title.setTextColor(
                Color.WHITE
        );

        title.setTextSize(24);

        title.setGravity(
                Gravity.CENTER
        );

        TextView message =
                new TextView(this);

        message.setText(
                "A second attentive face is visible.\n"
                        + "Sensitive content is temporarily hidden.\n"
                        + "Move away or look elsewhere to restore the screen."
        );

        message.setTextColor(
                Color.LTGRAY
        );

        message.setTextSize(15);

        message.setGravity(
                Gravity.CENTER
        );

        message.setPadding(
                0,
                18,
                0,
                0
        );

        center.addView(icon);

        center.addView(title);

        center.addView(message);

        shieldView.addView(
                center,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        WindowManager.LayoutParams lp =
                new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.OPAQUE
                );

        windowManager.addView(
                shieldView,
                lp
        );
    }

    // =========================================================
    // REMOVE
    // =========================================================

    private void removeOverlay() {

        if (shieldView == null ||
                windowManager == null) {

            return;
        }

        try {

            windowManager.removeView(
                    shieldView
            );

        } catch (Exception ignored) {
        }

        shieldView = null;
    }

    // =========================================================
    // DESTROY
    // =========================================================

    @Override
    public void onDestroy() {

        try {

            unregisterReceiver(
                    receiver
            );

        } catch (Exception ignored) {
        }

        removeOverlay();

        super.onDestroy();
    }
}