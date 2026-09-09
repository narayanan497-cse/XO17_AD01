package com.shieldguard.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleService;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraDetectionService
        extends LifecycleService
        implements SensorEventListener {

    // =========================================================
    // NOTIFICATION
    // =========================================================

    private static final int NOTIFICATION_ID = 3107;

    private static final String CHANNEL_ID =
            "privacy_shield";

    // =========================================================
    // CAMERA ANALYSIS RATE
    // =========================================================

    private static final long NORMAL_FRAME_INTERVAL_MS = 100L;

    private static final long MOVING_FRAME_INTERVAL_MS = 140L;

    private long lastAnalysisTime = 0L;

    // =========================================================
    // CAMERA
    // =========================================================

    private ExecutorService analyzerExecutor;

    private FaceDetector detector;

    private ProcessCameraProvider cameraProvider;

    private ShoulderSurfingDetector riskEngine;

    // =========================================================
    // SENSORS
    // =========================================================

    private SensorManager sensorManager;

    private Sensor accelerometer;

    private Sensor gyroscope;

    private long lastMovementTime = 0L;

    private volatile float accelerationMagnitude = 0f;

    private volatile float gyroscopeMagnitude = 0f;

    // Smoothed accelerometer values
    private float smoothX = 0f;
    private float smoothY = 0f;
    private float smoothZ = 0f;

    private boolean firstAccelerometerReading = true;

    // Current physical orientation
    private volatile int physicalRotation = 0;

    private volatile String physicalOrientation =
            "PORTRAIT";

    private static final long MOVEMENT_HOLD_MS = 900L;

    // =========================================================
    // ORIENTATION CHANGE DETECTION
    // =========================================================

    private int lastReportedRotation = 0;

    private long lastOrientationChangeTime = 0L;

    // =========================================================
    // CREATE
    // =========================================================

    @Override
    public void onCreate() {

        super.onCreate();

        createNotificationChannel();

        startAsForeground();

        setupSensors();

        analyzerExecutor =
                Executors.newSingleThreadExecutor();

        FaceDetectorOptions options =
                new FaceDetectorOptions.Builder()

                        .setPerformanceMode(
                                FaceDetectorOptions
                                        .PERFORMANCE_MODE_FAST
                        )

                        .setClassificationMode(
                                FaceDetectorOptions
                                        .CLASSIFICATION_MODE_ALL
                        )

                        .setMinFaceSize(
                                0.04f
                        )

                        .enableTracking()

                        .build();

        detector =
                FaceDetection.getClient(
                        options
                );

        riskEngine =
                new ShoulderSurfingDetector(
                        detector,

                        (
                                score,
                                faces,
                                active,
                                state,
                                details
                        ) -> {

                            ShieldBus.send(
                                    this,
                                    active,
                                    score,
                                    faces,
                                    state,
                                    details
                            );
                        }
                );

        bindCamera();
    }

    // =========================================================
    // SENSOR SETUP
    // =========================================================

    private void setupSensors() {

        sensorManager =
                (SensorManager)
                        getSystemService(
                                Context.SENSOR_SERVICE
                        );

        if (sensorManager == null) {
            return;
        }

        accelerometer =
                sensorManager.getDefaultSensor(
                        Sensor.TYPE_ACCELEROMETER
                );

        gyroscope =
                sensorManager.getDefaultSensor(
                        Sensor.TYPE_GYROSCOPE
                );

        if (accelerometer != null) {

            sensorManager.registerListener(
                    this,
                    accelerometer,
                    SensorManager.SENSOR_DELAY_NORMAL
            );
        }

        if (gyroscope != null) {

            sensorManager.registerListener(
                    this,
                    gyroscope,
                    SensorManager.SENSOR_DELAY_NORMAL
            );
        }
    }

    // =========================================================
    // SENSOR EVENTS
    // =========================================================

    @Override
    public void onSensorChanged(
            SensorEvent event
    ) {

        long now =
                SystemClock.elapsedRealtime();

        // -----------------------------------------------------
        // ACCELEROMETER
        // -----------------------------------------------------

        if (event.sensor.getType() ==
                Sensor.TYPE_ACCELEROMETER) {

            float x = event.values[0];

            float y = event.values[1];

            float z = event.values[2];

            float magnitude =
                    (float) Math.sqrt(
                            x * x +
                                    y * y +
                                    z * z
                    );

            accelerationMagnitude =
                    magnitude;

            // -------------------------------------------------
            // SMOOTH GRAVITY VECTOR
            // -------------------------------------------------

            if (firstAccelerometerReading) {

                smoothX = x;
                smoothY = y;
                smoothZ = z;

                firstAccelerometerReading =
                        false;

            } else {

                /*
                 * Low-pass filter.
                 *
                 * This removes most sudden noise but keeps
                 * the actual orientation of the phone.
                 */
                final float alpha = 0.15f;

                smoothX =
                        smoothX * (1f - alpha)
                                +
                                x * alpha;

                smoothY =
                        smoothY * (1f - alpha)
                                +
                                y * alpha;

                smoothZ =
                        smoothZ * (1f - alpha)
                                +
                                z * alpha;
            }

            // -------------------------------------------------
            // MOVEMENT DETECTION
            // -------------------------------------------------

            float gravityDifference =
                    Math.abs(
                            magnitude -
                                    SensorManager
                                            .GRAVITY_EARTH
                    );

            if (gravityDifference > 1.0f) {

                lastMovementTime =
                        now;
            }

            // -------------------------------------------------
            // PHYSICAL ROTATION
            // -------------------------------------------------

            calculatePhysicalRotation(
                    smoothX,
                    smoothY,
                    smoothZ,
                    now
            );
        }

        // -----------------------------------------------------
        // GYROSCOPE
        // -----------------------------------------------------

        if (event.sensor.getType() ==
                Sensor.TYPE_GYROSCOPE) {

            float x = event.values[0];

            float y = event.values[1];

            float z = event.values[2];

            float magnitude =
                    (float) Math.sqrt(
                            x * x +
                                    y * y +
                                    z * z
                    );

            gyroscopeMagnitude =
                    magnitude;

            /*
             * Detect rotational movement even if the
             * accelerometer magnitude remains close to gravity.
             */
            if (magnitude > 0.35f) {

                lastMovementTime =
                        now;
            }
        }
    }

    // =========================================================
    // PHYSICAL ROTATION
    // =========================================================

    private void calculatePhysicalRotation(
            float x,
            float y,
            float z,
            long now
    ) {

        /*
         * Calculate the direction of gravity relative
         * to the phone.
         *
         * We only need the X/Y direction to determine
         * portrait vs landscape.
         */

        double angle =
                Math.toDegrees(
                        Math.atan2(
                                y,
                                x
                        )
                );

        int rotation;

        /*
         * Four orientation zones.
         *
         * The 45-degree dead zones prevent rapid
         * flickering when the phone is between orientations.
         */

        if (angle >= -45 &&
                angle < 45) {

            rotation = 90;

        } else if (angle >= 45 &&
                angle < 135) {

            rotation = 180;

        } else if (angle >= -135 &&
                angle < -45) {

            rotation = 0;

        } else {

            rotation = 270;
        }

        /*
         * Only accept a new orientation after it remains
         * different for a short time.
         */
        if (rotation != lastReportedRotation) {

            if (lastOrientationChangeTime == 0L) {

                lastOrientationChangeTime =
                        now;

            } else if (
                    now -
                            lastOrientationChangeTime
                            >=
                            250L
            ) {

                lastReportedRotation =
                        rotation;

                physicalRotation =
                        rotation;

                lastOrientationChangeTime =
                        now;

                updateOrientationName(
                        rotation
                );
            }

        } else {

            lastOrientationChangeTime = 0L;
        }
    }

    // =========================================================
    // ORIENTATION NAME
    // =========================================================

    private void updateOrientationName(
            int rotation
    ) {

        switch (rotation) {

            case 0:

                physicalOrientation =
                        "PORTRAIT";

                break;

            case 90:

                physicalOrientation =
                        "LANDSCAPE";

                break;

            case 180:

                physicalOrientation =
                        "REVERSE PORTRAIT";

                break;

            case 270:

                physicalOrientation =
                        "REVERSE LANDSCAPE";

                break;

            default:

                physicalOrientation =
                        "UNKNOWN";

                break;
        }
    }

    // =========================================================
    // MOVEMENT
    // =========================================================

    private boolean isDeviceMoving() {

        long now =
                SystemClock.elapsedRealtime();

        return now -
                lastMovementTime
                <
                MOVEMENT_HOLD_MS;
    }

    // =========================================================
    // CAMERA
    // =========================================================

    private void bindCamera() {

        ListenableFuture<ProcessCameraProvider>
                future =
                ProcessCameraProvider
                        .getInstance(this);

        future.addListener(
                () -> {

                    try {

                        cameraProvider =
                                future.get();

                        CameraSelector selector =
                                CameraSelector
                                        .DEFAULT_FRONT_CAMERA;

                        ImageAnalysis analysis =
                                new ImageAnalysis.Builder()

                                        .setOutputImageFormat(
                                                ImageAnalysis
                                                        .OUTPUT_IMAGE_FORMAT_YUV_420_888
                                        )

                                        .setBackpressureStrategy(
                                                ImageAnalysis
                                                        .STRATEGY_KEEP_ONLY_LATEST
                                        )

                                        .build();

                        analysis.setAnalyzer(
                                analyzerExecutor,
                                image ->
                                        analyzeFrame(
                                                image
                                        )
                        );

                        cameraProvider.unbindAll();

                        cameraProvider.bindToLifecycle(
                                this,
                                selector,
                                analysis
                        );

                    } catch (Exception e) {

                        ShieldBus.send(
                                this,
                                false,
                                0,
                                0,
                                "CAMERA ERROR",

                                "Faces             0\n"
                                        + "Privacy risk      0 / 100\n"
                                        + "Attention         UNKNOWN\n"
                                        + "Proximity         UNKNOWN\n"
                                        + "Head direction    UNKNOWN\n"
                                        + "Voting            0 / 10\n"
                                        + "Persistence       0.0 sec\n"
                                        + "Motion            UNKNOWN\n"
                                        + "Lighting          UNKNOWN\n"
                                        + "Camera rotation   "
                                        + physicalRotation
                                        + "°"
                        );

                        stopSelf();
                    }

                },

                ContextCompat.getMainExecutor(
                        this
                )
        );
    }

    // =========================================================
    // FRAME ANALYSIS
    // =========================================================

    @OptIn(
            markerClass =
                    ExperimentalGetImage.class
    )
    private void analyzeFrame(
            @NonNull ImageProxy imageProxy
    ) {

        long now =
                SystemClock.elapsedRealtime();

        boolean moving =
                isDeviceMoving();

        long minimumInterval =
                moving
                        ? MOVING_FRAME_INTERVAL_MS
                        : NORMAL_FRAME_INTERVAL_MS;

        // -----------------------------------------------------
        // BATTERY THROTTLE
        // -----------------------------------------------------

        if (now -
                lastAnalysisTime
                <
                minimumInterval) {

            imageProxy.close();

            return;
        }

        lastAnalysisTime =
                now;

        android.media.Image mediaImage =
                imageProxy.getImage();

        if (mediaImage == null) {

            imageProxy.close();

            return;
        }

        // -----------------------------------------------------
        // LIGHT
        // -----------------------------------------------------

        float lightLevel =
                estimateBrightness(
                        imageProxy
                );

        // -----------------------------------------------------
        // CAMERA ROTATION
        // -----------------------------------------------------

        /*
         * IMPORTANT:
         *
         * We intentionally do NOT use CameraX's rotation
         * as the displayed rotation.
         *
         * CameraX rotation can remain fixed when the
         * Activity is portrait locked.
         *
         * Instead we use the physical accelerometer
         * orientation calculated above.
         */

        int rotation =
                physicalRotation;

        // -----------------------------------------------------
        // UPDATE DETECTOR
        // -----------------------------------------------------

        riskEngine.updateEnvironment(
                moving,
                lightLevel,
                rotation
        );

        // -----------------------------------------------------
        // ML KIT IMAGE ROTATION
        // -----------------------------------------------------

        int mlKitRotation =
                imageProxy
                        .getImageInfo()
                        .getRotationDegrees();

        InputImage image =
                InputImage.fromMediaImage(
                        mediaImage,
                        mlKitRotation
                );

        // -----------------------------------------------------
        // PROCESS
        // -----------------------------------------------------

        riskEngine.process(
                image,
                imageProxy::close
        );
    }

    // =========================================================
    // BRIGHTNESS
    // =========================================================

    private float estimateBrightness(
            ImageProxy imageProxy
    ) {

        try {

            ImageProxy.PlaneProxy[] planes =
                    imageProxy.getPlanes();

            if (planes == null ||
                    planes.length == 0) {

                return 50f;
            }

            ByteBuffer buffer =
                    planes[0].getBuffer();

            int rowStride =
                    planes[0].getRowStride();

            int pixelStride =
                    planes[0].getPixelStride();

            int width =
                    imageProxy.getWidth();

            int height =
                    imageProxy.getHeight();

            /*
             * Sample approximately 16 x 16 points.
             *
             * We don't process every pixel.
             */
            int stepX =
                    Math.max(
                            8,
                            width / 16
                    );

            int stepY =
                    Math.max(
                            8,
                            height / 16
                    );

            long total = 0L;

            int samples = 0;

            for (
                    int y = 0;
                    y < height;
                    y += stepY
            ) {

                int rowStart =
                        y * rowStride;

                for (
                        int x = 0;
                        x < width;
                        x += stepX
                ) {

                    int index =
                            rowStart +
                                    x *
                                            pixelStride;

                    if (index >= 0 &&
                            index <
                                    buffer.limit()) {

                        int value =
                                buffer.get(
                                        index
                                ) & 0xFF;

                        total += value;

                        samples++;
                    }
                }
            }

            if (samples == 0) {

                return 50f;
            }

            float average =
                    total /
                            (float) samples;

            return
                    (average / 255f)
                            * 100f;

        } catch (Exception ignored) {

            return 50f;
        }
    }

    // =========================================================
    // FOREGROUND SERVICE
    // =========================================================

    private void startAsForeground() {

        Notification notification =
                new NotificationCompat.Builder(
                        this,
                        CHANNEL_ID
                )

                        .setSmallIcon(
                                android.R.drawable
                                        .ic_lock_lock
                        )

                        .setContentTitle(
                                "PrivacyShield is active"
                        )

                        .setContentText(
                                "Adaptive shoulder-surfing protection is running."
                        )

                        .setOngoing(true)

                        .setCategory(
                                NotificationCompat
                                        .CATEGORY_SERVICE
                        )

                        .build();

        if (Build.VERSION.SDK_INT >= 29) {

            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo
                            .FOREGROUND_SERVICE_TYPE_CAMERA
            );

        } else {

            startForeground(
                    NOTIFICATION_ID,
                    notification
            );
        }
    }

    // =========================================================
    // NOTIFICATION CHANNEL
    // =========================================================

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT < 26) {

            return;
        }

        NotificationManager manager =
                getSystemService(
                        NotificationManager.class
                );

        if (manager != null) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "PrivacyShield",
                            NotificationManager
                                    .IMPORTANCE_LOW
                    );

            channel.setDescription(
                    "Adaptive PrivacyShield camera protection"
            );

            manager.createNotificationChannel(
                    channel
            );
        }
    }

    // =========================================================
    // ACCURACY
    // =========================================================

    @Override
    public void onAccuracyChanged(
            Sensor sensor,
            int accuracy
    ) {

        // Not required.
    }

    // =========================================================
    // DESTROY
    // =========================================================

    @Override
    public void onDestroy() {

        ShieldBus.send(
                this,
                false,
                0,
                0,
                "STOPPED",

                "Faces             0\n"
                        + "Privacy risk      0 / 100\n"
                        + "Attention         LOW\n"
                        + "Proximity         FAR\n"
                        + "Head direction    AWAY\n"
                        + "Voting            0 / 10\n"
                        + "Persistence       0.0 sec\n"
                        + "Motion            UNKNOWN\n"
                        + "Lighting          UNKNOWN\n"
                        + "Camera rotation   UNKNOWN"
        );

        if (sensorManager != null) {

            sensorManager.unregisterListener(
                    this
            );
        }

        if (cameraProvider != null) {

            cameraProvider.unbindAll();
        }

        if (detector != null) {

            detector.close();
        }

        if (analyzerExecutor != null) {

            analyzerExecutor.shutdownNow();
        }

        super.onDestroy();
    }
}