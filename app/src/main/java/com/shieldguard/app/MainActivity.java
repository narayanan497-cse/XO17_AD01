package com.shieldguard.app;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity
        extends AppCompatActivity {

    private static final int CAMERA_REQUEST = 11;

    private TextView statusText;

    private TextView statusDetail;

    private TextView accessibilityStatus;

    private Button startButton;

    @Override
    protected void onCreate(
            @Nullable Bundle savedInstanceState
    ) {

        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_main
        );

        statusText =
                findViewById(
                        R.id.statusText
                );

        statusDetail =
                findViewById(
                        R.id.statusDetail
                );

        accessibilityStatus =
                findViewById(
                        R.id.accessibilityStatus
                );

        startButton =
                findViewById(
                        R.id.startButton
                );

        Button openDemo =
                findViewById(
                        R.id.openDemoButton
                );

        Button accessibility =
                findViewById(
                        R.id.accessibilityButton
                );

        startButton.setOnClickListener(
                v -> startProtection()
        );

        openDemo.setOnClickListener(
                v -> {

                    if (ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED) {

                        startProtection();

                        startActivity(
                                new Intent(
                                        this,
                                        ProtectionActivity.class
                                )
                        );

                    } else {

                        ensureCameraPermission();
                    }
                }
        );

        accessibility.setOnClickListener(
                v ->
                        startActivity(
                                new Intent(
                                        Settings.ACTION_ACCESSIBILITY_SETTINGS
                                )
                        )
        );

        ensureCameraPermission();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == CAMERA_REQUEST &&
                grantResults.length > 0 &&
                grantResults[0] ==
                        PackageManager.PERMISSION_GRANTED) {

            startButton.setEnabled(true);

            statusDetail.setText(
                    "Camera permission granted. "
                            + "Ready for the live protection demo."
            );
        }
    }

    @Override
    protected void onResume() {

        super.onResume();

        updateAccessibilityStatus();
    }

    private void ensureCameraPermission() {

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.CAMERA
                    },
                    CAMERA_REQUEST
            );

        } else {

            startButton.setEnabled(true);
        }
    }

    private void startProtection() {

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) != PackageManager.PERMISSION_GRANTED) {

            ensureCameraPermission();

            return;
        }

        Intent service =
                new Intent(
                        this,
                        CameraDetectionService.class
                );

        if (Build.VERSION.SDK_INT >= 26) {

            ContextCompat.startForegroundService(
                    this,
                    service
            );

        } else {

            startService(service);
        }

        statusText.setText(
                "●  PROTECTION ACTIVE"
        );

        statusText.setTextColor(
                getColor(
                        R.color.shield_safe
                )
        );

        statusDetail.setText(
                "Camera is analyzing locally. "
                        + "No frames are stored."
        );
    }

    private void updateAccessibilityStatus() {

        String enabled =
                Settings.Secure.getString(
                        getContentResolver(),
                        Settings.Secure
                                .ENABLED_ACCESSIBILITY_SERVICES
                );

        String expected =
                new ComponentName(
                        this,
                        ShieldAccessibilityService.class
                ).flattenToString();

        boolean active =
                !TextUtils.isEmpty(enabled)
                        &&
                        enabled.contains(expected);

        accessibilityStatus.setText(
                active
                        ? "Global Shield: READY — accessibility overlay enabled"
                        : "Global Shield: enable accessibility permission for cross-app shielding"
        );

        accessibilityStatus.setTextColor(
                getColor(
                        active
                                ? R.color.shield_safe
                                : R.color.shield_muted
                )
        );
    }
}