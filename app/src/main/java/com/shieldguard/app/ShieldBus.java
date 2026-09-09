package com.shieldguard.app;

import android.content.Context;
import android.content.Intent;

public final class ShieldBus {

    public static final String ACTION_SHIELD =
            "com.shieldguard.app.ACTION_SHIELD";

    public static final String EXTRA_ACTIVE =
            "active";

    public static final String EXTRA_SCORE =
            "score";

    public static final String EXTRA_FACES =
            "faces";

    public static final String EXTRA_STATE =
            "state";

    public static final String EXTRA_DETAILS =
            "details";

    private ShieldBus() {
    }

    public static void send(
            Context context,
            boolean active,
            int score,
            int faces,
            String state,
            String details
    ) {

        Intent intent =
                new Intent(ACTION_SHIELD)
                        .setPackage(
                                context.getPackageName()
                        )
                        .putExtra(
                                EXTRA_ACTIVE,
                                active
                        )
                        .putExtra(
                                EXTRA_SCORE,
                                score
                        )
                        .putExtra(
                                EXTRA_FACES,
                                faces
                        )
                        .putExtra(
                                EXTRA_STATE,
                                state
                        )
                        .putExtra(
                                EXTRA_DETAILS,
                                details
                        );

        context.sendBroadcast(intent);
    }
}