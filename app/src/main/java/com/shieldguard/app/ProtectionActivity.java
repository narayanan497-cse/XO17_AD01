package com.shieldguard.app;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class ProtectionActivity
        extends AppCompatActivity {

    private LinearLayout sensitiveContent;

    private FrameLayout shieldOverlay;

    private TextView threatMetric;

    private TextView liveStatus;

    private TextView liveDetails;

    // =========================================================
    // BROADCAST RECEIVER
    // =========================================================

    private final BroadcastReceiver receiver =
            new BroadcastReceiver() {

                @Override
                public void onReceive(
                        Context context,
                        Intent intent
                ) {

                    boolean active =
                            intent.getBooleanExtra(
                                    ShieldBus.EXTRA_ACTIVE,
                                    false
                            );

                    int score =
                            intent.getIntExtra(
                                    ShieldBus.EXTRA_SCORE,
                                    0
                            );

                    int faces =
                            intent.getIntExtra(
                                    ShieldBus.EXTRA_FACES,
                                    0
                            );

                    String state =
                            intent.getStringExtra(
                                    ShieldBus.EXTRA_STATE
                            );

                    String details =
                            intent.getStringExtra(
                                    ShieldBus.EXTRA_DETAILS
                            );

                    renderShield(
                            active,
                            score,
                            faces,
                            state,
                            details
                    );
                }
            };

    // =========================================================
    // CREATE
    // =========================================================

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {

        super.onCreate(savedInstanceState);

        /*
         * Prevent screenshots / screen recording of the
         * sensitive demo.
         */
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );

        setContentView(
                R.layout.activity_protection
        );

        sensitiveContent =
                findViewById(
                        R.id.sensitiveContent
                );

        shieldOverlay =
                findViewById(
                        R.id.shieldOverlay
                );

        threatMetric =
                findViewById(
                        R.id.threatMetric
                );

        liveStatus =
                findViewById(
                        R.id.liveStatus
                );

        liveDetails =
                findViewById(
                        R.id.liveDetails
                );

        Button back =
                findViewById(
                        R.id.backButton
                );

        back.setOnClickListener(
                v -> finish()
        );
    }

    // =========================================================
    // RECEIVER
    // =========================================================

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onStart() {

        super.onStart();

        IntentFilter filter =
                new IntentFilter(
                        ShieldBus.ACTION_SHIELD
                );

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU) {

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

    @Override
    protected void onStop() {

        try {

            unregisterReceiver(
                    receiver
            );

        } catch (Exception ignored) {
        }

        super.onStop();
    }

    // =========================================================
    // RENDER
    // =========================================================

    @SuppressLint("SetTextI18n")
    private void renderShield(
            boolean active,
            int score,
            int faces,
            String state,
            String details
    ) {

        if (state == null ||
                state.trim().isEmpty()) {

            state =
                    active
                            ? "SHIELD ACTIVE"
                            : "SAFE";
        }

        if (details == null ||
                details.trim().isEmpty()) {

            details =
                    "Faces             "
                            + faces
                            + "\n"
                            + "Privacy risk      "
                            + score
                            + " / 100\n"
                            + "Attention         UNKNOWN\n"
                            + "Proximity         UNKNOWN\n"
                            + "Head direction    UNKNOWN\n"
                            + "Voting            0 / 10\n"
                            + "Persistence       0.0 sec";
        }

        // -----------------------------------------------------
        // Risk
        // -----------------------------------------------------

        threatMetric.setText(
                "Privacy risk: "
                        + score
                        + " / 100"
                        + "   •   Faces detected: "
                        + faces
        );

        // -----------------------------------------------------
        // Live status
        // -----------------------------------------------------

        liveStatus.setText(
                "● " + state
        );

        // -----------------------------------------------------
        // Live diagnostic details
        // -----------------------------------------------------

        liveDetails.setText(
                details
        );

        // -----------------------------------------------------
        // Shield
        // -----------------------------------------------------

        shieldOverlay.setVisibility(
                active
                        ? View.VISIBLE
                        : View.GONE
        );

        /*
         * IMPORTANT:
         *
         * sensitiveContent is blurred.
         *
         * liveMonitor is NOT inside sensitiveContent.
         *
         * Therefore the live monitor remains sharp.
         */

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.S) {

            sensitiveContent.setRenderEffect(
                    active
                            ? RenderEffect.createBlurEffect(
                            24f,
                            24f,
                            Shader.TileMode.CLAMP
                    )
                            : null
            );

        } else {

            sensitiveContent.setAlpha(
                    active
                            ? 0.06f
                            : 1f
            );
        }
    }
}