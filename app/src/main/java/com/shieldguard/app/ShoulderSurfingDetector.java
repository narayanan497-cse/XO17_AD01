package com.shieldguard.app;

import android.os.SystemClock;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

public class ShoulderSurfingDetector {

    // ============================================================
    // CONFIGURATION
    // ============================================================

    private static final int HISTORY_SIZE = 10;

    // Voting thresholds
    private static final int NORMAL_REQUIRED_VOTES = 6;
    private static final int MOVING_REQUIRED_VOTES = 7;

    // Risk thresholds
    private static final int SUSPICIOUS_FRAME_SCORE = 55;
    private static final int ACTIVATE_SCORE = 70;
    private static final int CLEAR_SCORE = 42;

    // Persistence
    private static final long NORMAL_CONFIRMATION_MS = 900;
    private static final long MOVING_CONFIRMATION_MS = 1200;

    // Clear grace period
    private static final long NORMAL_CLEAR_GRACE_MS = 700;
    private static final long MOVING_CLEAR_GRACE_MS = 1500;

    // Candidate disappearance grace
    private static final long CANDIDATE_LOST_GRACE_MS = 700;

    // Face geometry
    private static final float REFERENCE_AREA = 640f * 480f;

    private static final float MIN_SECONDARY_CAMERA_RATIO = 0.018f;
    private static final float MIN_RELATIVE_FACE_SIZE = 0.15f;
    private static final float MAX_RELATIVE_FACE_SIZE = 1.80f;

    private static final float MAX_HORIZONTAL_DISTANCE = 2.80f;
    private static final float MAX_VERTICAL_DISTANCE = 2.20f;

    private static final float MIN_SIDE_DISTANCE = 0.12f;

    // Head direction
    private static final float MAX_YAW = 38f;
    private static final float MAX_ROLL = 42f;

    // Smoothing
    private static final float RISK_EMA_ALPHA = 0.28f;

    // ============================================================
    // TYPES
    // ============================================================

    public interface DetectionCallback {
        void onDetection(
                int score,
                int faces,
                boolean active,
                String state,
                String details
        );
    }

    private static class Candidate {

        Face face;

        float area;
        float centerX;
        float centerY;

        float relativeArea;
        float horizontalDistance;
        float verticalDistance;

        boolean valid;

        int score;

        Candidate(Face face) {
            this.face = face;
        }
    }

    // ============================================================
    // FIELDS
    // ============================================================

    private final FaceDetector detector;
    private final DetectionCallback callback;

    private final Deque<Boolean> votingHistory =
            new ArrayDeque<>();

    private volatile boolean deviceMoving = false;
    private volatile float lightLevel = 50f;
    private volatile int rotationDegrees = 0;

    private boolean active = false;

    private float smoothedRisk = 0f;

    // Candidate persistence
    private long candidatePresentSince = 0L;
    private long lastCandidateSeen = 0L;

    private boolean candidateCurrentlyVisible = false;

    // Threat persistence
    private long suspiciousSince = 0L;

    // Clear timing
    private long lastSuspiciousTime = 0L;

    // Demo/live monitor values
    private int liveVotes = 0;
    private long livePersistenceMs = 0L;
    private int liveFaceCount = 0;

    private long frameCounter = 0L;

    // Used to stabilize candidate matching.
    private float previousCandidateX = -1f;
    private float previousCandidateY = -1f;
    private float previousCandidateArea = -1f;

    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public ShoulderSurfingDetector(
            FaceDetector detector,
            DetectionCallback callback
    ) {
        this.detector = detector;
        this.callback = callback;
    }

    // ============================================================
    // ENVIRONMENT
    // ============================================================

    public void updateEnvironment(
            boolean moving,
            float lightPercent,
            int rotation
    ) {
        deviceMoving = moving;

        if (Float.isNaN(lightPercent)) {
            lightPercent = 50f;
        }

        lightLevel = clamp(
                lightPercent,
                0f,
                100f
        );

        rotationDegrees = normalizeRotation(rotation);
    }

    // ============================================================
    // PROCESS FRAME
    // ============================================================

    public void process(
            InputImage image,
            Runnable onComplete
    ) {

        frameCounter++;

        detector.process(image)
                .addOnSuccessListener(
                        new OnSuccessListener<List<Face>>() {
                            @Override
                            public void onSuccess(List<Face> faces) {

                                try {
                                    analyzeFaces(faces);
                                } finally {
                                    if (onComplete != null) {
                                        onComplete.run();
                                    }
                                }
                            }
                        }
                )
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {

                                try {
                                    handleDetectionFailure();
                                } finally {
                                    if (onComplete != null) {
                                        onComplete.run();
                                    }
                                }
                            }
                        }
                );
    }

    // ============================================================
    // FACE ANALYSIS
    // ============================================================

    private synchronized void analyzeFaces(
            List<Face> detectedFaces
    ) {

        long now = SystemClock.elapsedRealtime();

        if (detectedFaces == null) {
            detectedFaces = Collections.emptyList();
        }

        liveFaceCount = detectedFaces.size();

        if (detectedFaces.isEmpty()) {
            handleNoFaces(now);
            return;
        }

        List<Face> faces = new ArrayList<>(detectedFaces);

        Collections.sort(
                faces,
                new Comparator<Face>() {
                    @Override
                    public int compare(Face a, Face b) {

                        float areaA =
                                a.getBoundingBox().width()
                                        * a.getBoundingBox().height();

                        float areaB =
                                b.getBoundingBox().width()
                                        * b.getBoundingBox().height();

                        return Float.compare(
                                areaB,
                                areaA
                        );
                    }
                }
        );

        // Largest face is assumed to be the owner/user.
        Face owner = faces.get(0);

        Candidate candidate =
                findSecondaryCandidate(
                        owner,
                        faces
                );

        if (candidate == null) {

            candidateCurrentlyVisible = false;

            addVote(false);

            updateLivePersistence(now, false);

            smoothedRisk = smoothRisk(
                    smoothedRisk,
                    Math.max(0f, smoothedRisk - 8f)
            );

            updateActiveState(
                    false,
                    (int) smoothedRisk,
                    now
            );

            sendResult(
                    (int) smoothedRisk,
                    faces.size(),
                    active,
                    active
                            ? "SHIELD ACTIVE"
                            : "SAFE",
                    buildDetails(
                            faces.size(),
                            (int) smoothedRisk,
                            owner,
                            null,
                            now
                    )
            );

            return;
        }

        // --------------------------------------------------------
        // Candidate found
        // --------------------------------------------------------

        candidateCurrentlyVisible = true;
        lastCandidateSeen = now;

        if (candidatePresentSince == 0L) {
            candidatePresentSince = now;
        }

        updateLivePersistence(now, true);

        // IMPORTANT:
        // Do NOT reset voting history because ML Kit changed
        // tracking ID. This was causing Voting to stay at 0/10.
        stabilizeCandidate(candidate);

        int score =
                calculateRiskScore(
                        owner,
                        candidate
                );

        boolean suspicious =
                score >= SUSPICIOUS_FRAME_SCORE;

        addVote(suspicious);

        if (suspicious) {

            if (suspiciousSince == 0L) {
                suspiciousSince = now;
            }

            lastSuspiciousTime = now;

        } else {

            // Do not immediately destroy persistence.
            // This makes the system tolerant to one bad frame.
            if (now - lastSuspiciousTime > 400) {
                suspiciousSince = 0L;
            }
        }

        smoothedRisk =
                smoothRisk(
                        smoothedRisk,
                        score
                );

        int finalScore =
                Math.round(smoothedRisk);

        updateActiveState(
                suspicious,
                finalScore,
                now
        );

        sendResult(
                finalScore,
                faces.size(),
                active,
                active
                        ? "SHIELD ACTIVE"
                        : suspicious
                        ? "MONITORING"
                        : "SAFE",
                buildDetails(
                        faces.size(),
                        finalScore,
                        owner,
                        candidate,
                        now
                )
        );
    }

    // ============================================================
    // SECONDARY FACE
    // ============================================================

    private Candidate findSecondaryCandidate(
            Face owner,
            List<Face> faces
    ) {

        if (faces.size() <= 1) {
            return null;
        }

        float ownerArea =
                Math.max(
                        1f,
                        owner.getBoundingBox().width()
                                * owner.getBoundingBox().height()
                );

        float ownerCenterX =
                owner.getBoundingBox().centerX();

        float ownerCenterY =
                owner.getBoundingBox().centerY();

        Candidate best = null;

        for (int i = 1; i < faces.size(); i++) {

            Face face = faces.get(i);

            float width =
                    face.getBoundingBox().width();

            float height =
                    face.getBoundingBox().height();

            float area =
                    Math.max(
                            1f,
                            width * height
                    );

            float ratio =
                    area / REFERENCE_AREA;

            float relativeSize =
                    area / ownerArea;

            float dx =
                    Math.abs(
                            face.getBoundingBox().centerX()
                                    - ownerCenterX
                    ) / Math.max(
                            1f,
                            owner.getBoundingBox().width()
                    );

            float dy =
                    Math.abs(
                            face.getBoundingBox().centerY()
                                    - ownerCenterY
                    ) / Math.max(
                            1f,
                            owner.getBoundingBox().height()
                    );

            if (ratio < MIN_SECONDARY_CAMERA_RATIO) {
                continue;
            }

            if (relativeSize < MIN_RELATIVE_FACE_SIZE) {
                continue;
            }

            if (relativeSize > MAX_RELATIVE_FACE_SIZE) {
                continue;
            }

            if (dx > MAX_HORIZONTAL_DISTANCE) {
                continue;
            }

            if (dy > MAX_VERTICAL_DISTANCE) {
                continue;
            }

            Candidate candidate =
                    new Candidate(face);

            candidate.area = area;
            candidate.centerX =
                    face.getBoundingBox().centerX();
            candidate.centerY =
                    face.getBoundingBox().centerY();

            candidate.relativeArea =
                    relativeSize;

            candidate.horizontalDistance =
                    dx;

            candidate.verticalDistance =
                    dy;

            candidate.valid = true;

            if (best == null
                    || candidate.area > best.area) {

                best = candidate;
            }
        }

        return best;
    }

    // ============================================================
    // CANDIDATE STABILIZATION
    // ============================================================

    private void stabilizeCandidate(
            Candidate candidate
    ) {

        if (candidate == null) {
            return;
        }

        float x = candidate.centerX;
        float y = candidate.centerY;
        float area = candidate.area;

        if (previousCandidateX < 0f) {

            previousCandidateX = x;
            previousCandidateY = y;
            previousCandidateArea = area;

            return;
        }

        // Smooth location instead of resetting when tracking ID changes.
        previousCandidateX =
                previousCandidateX * 0.70f
                        + x * 0.30f;

        previousCandidateY =
                previousCandidateY * 0.70f
                        + y * 0.30f;

        previousCandidateArea =
                previousCandidateArea * 0.70f
                        + area * 0.30f;
    }

    // ============================================================
    // RISK SCORE
    // ============================================================

    private int calculateRiskScore(
            Face owner,
            Candidate candidate
    ) {

        if (candidate == null) {
            return 0;
        }

        int score = 0;

        Face face = candidate.face;

        // --------------------------------------------------------
        // Second person's size
        // --------------------------------------------------------

        float sizeRatio =
                candidate.relativeArea;

        if (sizeRatio >= 0.08f) {
            score += 18;
        } else if (sizeRatio >= 0.04f) {
            score += 12;
        } else {
            score += 6;
        }

        // --------------------------------------------------------
        // Horizontal proximity
        // --------------------------------------------------------

        float horizontal =
                candidate.horizontalDistance;

        if (horizontal <= 0.75f) {
            score += 22;
        } else if (horizontal <= 1.30f) {
            score += 16;
        } else if (horizontal <= 2.0f) {
            score += 10;
        } else {
            score += 4;
        }

        // --------------------------------------------------------
        // Vertical proximity
        // --------------------------------------------------------

        float vertical =
                candidate.verticalDistance;

        if (vertical <= 0.70f) {
            score += 12;
        } else if (vertical <= 1.30f) {
            score += 8;
        } else {
            score += 3;
        }

        // --------------------------------------------------------
        // Side-by-side positioning
        // --------------------------------------------------------

        float ownerX =
                owner.getBoundingBox().centerX();

        float candidateX =
                face.getBoundingBox().centerX();

        float distanceX =
                Math.abs(candidateX - ownerX);

        float ownerWidth =
                Math.max(
                        1f,
                        owner.getBoundingBox().width()
                );

        float normalizedSide =
                distanceX / ownerWidth;

        if (normalizedSide >= MIN_SIDE_DISTANCE) {
            score += 8;
        }

        // --------------------------------------------------------
        // Head direction
        // --------------------------------------------------------

        Float yaw =
                face.getHeadEulerAngleY();

        Float roll =
                face.getHeadEulerAngleZ();

        if (yaw != null) {

            float absYaw =
                    Math.abs(yaw);

            if (absYaw <= MAX_YAW) {
                score += 15;
            } else if (absYaw <= 60f) {
                score += 8;
            }
        }

        if (roll != null) {

            float absRoll =
                    Math.abs(roll);

            if (absRoll <= MAX_ROLL) {
                score += 5;
            }
        }

        // --------------------------------------------------------
        // Lighting adjustment
        // --------------------------------------------------------

        if (lightLevel < 20f) {

            // Very dark image = reduce confidence.
            score -= 10;

        } else if (lightLevel < 35f) {

            score -= 5;

        } else if (lightLevel > 80f) {

            score += 2;
        }

        // --------------------------------------------------------
        // Movement adaptation
        // --------------------------------------------------------

        if (deviceMoving) {

            // Do not make movement itself a threat.
            // Slightly increase required confidence later.
            score -= 2;
        }

        return clamp(
                score,
                0,
                100
        );
    }

    // ============================================================
    // VOTING
    // ============================================================

    private void addVote(
            boolean suspicious
    ) {

        votingHistory.addLast(suspicious);

        while (
                votingHistory.size()
                        > HISTORY_SIZE
        ) {
            votingHistory.removeFirst();
        }

        int count = 0;

        for (Boolean vote : votingHistory) {

            if (Boolean.TRUE.equals(vote)) {
                count++;
            }
        }

        liveVotes = count;
    }

    private int getVoteCount() {

        int count = 0;

        for (Boolean vote : votingHistory) {

            if (Boolean.TRUE.equals(vote)) {
                count++;
            }
        }

        return count;
    }

    // ============================================================
    // PERSISTENCE
    // ============================================================

    private void updateLivePersistence(
            long now,
            boolean candidateVisible
    ) {

        if (candidateVisible) {

            if (candidatePresentSince == 0L) {
                candidatePresentSince = now;
            }

            livePersistenceMs =
                    Math.max(
                            0L,
                            now - candidatePresentSince
                    );

            return;
        }

        if (candidatePresentSince == 0L) {
            livePersistenceMs = 0L;
            return;
        }

        long lostFor =
                now - lastCandidateSeen;

        if (lostFor > CANDIDATE_LOST_GRACE_MS) {

            candidatePresentSince = 0L;
            livePersistenceMs = 0L;

            previousCandidateX = -1f;
            previousCandidateY = -1f;
            previousCandidateArea = -1f;
        } else {

            livePersistenceMs =
                    Math.max(
                            0L,
                            lastCandidateSeen
                                    - candidatePresentSince
                    );
        }
    }

    // ============================================================
    // ACTIVE STATE
    // ============================================================

    private void updateActiveState(
            boolean suspicious,
            int score,
            long now
    ) {

        int votes =
                getVoteCount();

        int requiredVotes =
                deviceMoving
                        ? MOVING_REQUIRED_VOTES
                        : NORMAL_REQUIRED_VOTES;

        long requiredPersistence =
                deviceMoving
                        ? MOVING_CONFIRMATION_MS
                        : NORMAL_CONFIRMATION_MS;

        // Low lighting requires more persistence.
        if (lightLevel < 35f) {
            requiredPersistence += 400;
        }

        boolean enoughVotes =
                votes >= requiredVotes;

        boolean enoughPersistence =
                suspiciousSince != 0L
                        && now - suspiciousSince
                        >= requiredPersistence;

        if (!active) {

            if (enoughVotes
                    && enoughPersistence
                    && score >= ACTIVATE_SCORE) {

                active = true;
            }

            return;
        }

        // --------------------------------------------------------
        // Already active
        // --------------------------------------------------------

        long clearGrace =
                deviceMoving
                        ? MOVING_CLEAR_GRACE_MS
                        : NORMAL_CLEAR_GRACE_MS;

        boolean shouldClear =
                votes < 3
                        && score < CLEAR_SCORE
                        && (
                        lastSuspiciousTime == 0L
                                || now - lastSuspiciousTime
                                > clearGrace
                );

        if (shouldClear) {

            active = false;
            suspiciousSince = 0L;
        }
    }

    // ============================================================
    // NO FACE
    // ============================================================

    private void handleNoFaces(
            long now
    ) {

        addVote(false);

        updateLivePersistence(
                now,
                false
        );

        smoothedRisk =
                smoothRisk(
                        smoothedRisk,
                        0f
                );

        if (active) {

            if (
                    lastSuspiciousTime != 0L
                            && now - lastSuspiciousTime
                            > MOVING_CLEAR_GRACE_MS
            ) {
                active = false;
            }
        }

        sendResult(
                Math.round(smoothedRisk),
                0,
                active,
                active
                        ? "SHIELD ACTIVE"
                        : "SAFE",
                buildDetails(
                        0,
                        Math.round(smoothedRisk),
                        null,
                        null,
                        now
                )
        );
    }

    // ============================================================
    // DETECTION FAILURE
    // ============================================================

    private synchronized void handleDetectionFailure() {

        long now =
                SystemClock.elapsedRealtime();

        // Do not destroy state on a single ML Kit failure.
        sendResult(
                Math.round(smoothedRisk),
                liveFaceCount,
                active,
                active
                        ? "SHIELD ACTIVE"
                        : "MONITORING",
                buildDetails(
                        liveFaceCount,
                        Math.round(smoothedRisk),
                        null,
                        null,
                        now
                )
        );
    }

    // ============================================================
    // DETAILS FOR UI
    // ============================================================

    private String buildDetails(
            int faces,
            int score,
            Face owner,
            Candidate candidate,
            long now
    ) {

        String attention =
                "LOW";

        String proximity =
                "FAR";

        String direction =
                "AWAY";

        if (candidate != null) {

            float horizontal =
                    candidate.horizontalDistance;

            if (horizontal < 0.8f) {
                proximity = "VERY CLOSE";
            } else if (horizontal < 1.4f) {
                proximity = "CLOSE";
            } else if (horizontal < 2.0f) {
                proximity = "MEDIUM";
            } else {
                proximity = "FAR";
            }

            Float yaw =
                    candidate.face
                            .getHeadEulerAngleY();

            if (yaw != null) {

                if (Math.abs(yaw) <= MAX_YAW) {
                    direction = "TOWARD";
                } else {
                    direction = "AWAY";
                }
            }

            if (score >= 70) {
                attention = "HIGH";
            } else if (score >= 45) {
                attention = "MEDIUM";
            } else {
                attention = "LOW";
            }
        }

        float persistenceSeconds =
                livePersistenceMs / 1000f;

        String motion =
                deviceMoving
                        ? "MOVING"
                        : "STABLE";

        String lighting;

        if (lightLevel < 20f) {
            lighting = "DARK";
        } else if (lightLevel < 40f) {
            lighting = "LOW";
        } else if (lightLevel < 75f) {
            lighting = "NORMAL";
        } else {
            lighting = "BRIGHT";
        }

        return String.format(
                Locale.US,

                "Faces             %d\n" +
                        "Privacy risk      %d / 100\n" +
                        "Attention         %s\n" +
                        "Proximity         %s\n" +
                        "Head direction    %s\n" +
                        "Voting            %d / %d\n" +
                        "Persistence       %.1f sec\n" +
                        "Motion            %s\n" +
                        "Lighting          %s (%.0f%%)\n" +
                        "Camera angle      %s",

                faces,
                score,
                attention,
                proximity,
                direction,
                liveVotes,
                HISTORY_SIZE,
                persistenceSeconds,
                motion,
                lighting,
                lightLevel,
                getRotationName(rotationDegrees)
        );
    }

    // ============================================================
    // SEND RESULT
    // ============================================================

    private void sendResult(
            int score,
            int faces,
            boolean active,
            String state,
            String details
    ) {

        if (callback == null) {
            return;
        }

        callback.onDetection(
                clamp(score, 0, 100),
                Math.max(0, faces),
                active,
                state,
                details
        );
    }

    // ============================================================
    // RISK SMOOTHING
    // ============================================================

    private float smoothRisk(
            float oldValue,
            float newValue
    ) {

        if (oldValue == 0f) {
            return newValue;
        }

        return oldValue
                * (1f - RISK_EMA_ALPHA)
                + newValue
                * RISK_EMA_ALPHA;
    }

    // ============================================================
    // ROTATION
    // ============================================================

    private int normalizeRotation(
            int rotation
    ) {

        rotation %= 360;

        if (rotation < 0) {
            rotation += 360;
        }

        if (rotation < 45
                || rotation >= 315) {

            return 0;
        }

        if (rotation < 135) {
            return 90;
        }

        if (rotation < 225) {
            return 180;
        }

        return 270;
    }

    private String getRotationName(
            int rotation
    ) {

        switch (normalizeRotation(rotation)) {

            case 90:
                return "LANDSCAPE LEFT";

            case 180:
                return "PORTRAIT UPSIDE DOWN";

            case 270:
                return "LANDSCAPE RIGHT";

            default:
                return "PORTRAIT";
        }
    }

    // ============================================================
    // UTILS
    // ============================================================

    private static int clamp(
            int value,
            int min,
            int max
    ) {

        return Math.max(
                min,
                Math.min(
                        max,
                        value
                )
        );
    }

    private static float clamp(
            float value,
            float min,
            float max
    ) {

        return Math.max(
                min,
                Math.min(
                        max,
                        value
                )
        );
    }

    // ============================================================
    // CLEANUP
    // ============================================================

    public void close() {

        votingHistory.clear();

        active = false;

        candidatePresentSince = 0L;
        lastCandidateSeen = 0L;
        suspiciousSince = 0L;
        lastSuspiciousTime = 0L;

        liveVotes = 0;
        livePersistenceMs = 0L;
        liveFaceCount = 0;

        previousCandidateX = -1f;
        previousCandidateY = -1f;
        previousCandidateArea = -1f;

        if (detector != null) {
            detector.close();
        }
    }
}