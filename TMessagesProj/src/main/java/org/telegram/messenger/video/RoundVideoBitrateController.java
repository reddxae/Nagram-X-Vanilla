package org.telegram.messenger.video;

public class RoundVideoBitrateController {
    public static final int NO_BITRATE_UPDATE = -1;

    private static final long SERVER_LIMIT_SAFETY_BYTES = 256L * 1024L;
    private static final long FINAL_BITRATE_RESERVE_BYTES = 384L * 1024L;
    private static final long MIN_AVAILABLE_BYTES = 512L * 1024L;
    private static final long MIN_LIVE_UPDATE_ELAPSED_MS = 10_000L;
    private static final long LIVE_UPDATE_INTERVAL_MS = 1_000L;
    private static final int LIVE_UPDATE_MAX_STEP = 64 * 1024;
    private static final float LIVE_UPDATE_SAFETY_FACTOR = 0.92f;

    private int currentVideoBitrate;
    private long lastLiveUpdateMs;
    private volatile long muxedSizeEstimate;

    public void reset(int initialVideoBitrate) {
        currentVideoBitrate = Math.max(0, initialVideoBitrate);
        lastLiveUpdateMs = 0;
        muxedSizeEstimate = 0;
    }

    public void onBitrateApplied(int bitrate) {
        currentVideoBitrate = Math.max(0, bitrate);
    }

    public void updateMuxedSizeEstimate(long availableSize) {
        if (availableSize > 0) {
            muxedSizeEstimate = Math.max(muxedSizeEstimate, availableSize);
        }
    }

    public int calculateFinalVideoBitrate(long durationMs, int audioBitrate) {
        long safeDurationMs = Math.max(1000L, durationMs);
        long availableBytes = Math.max(MIN_AVAILABLE_BYTES, RoundVideoEncodingOptions.SERVER_FILE_SIZE_LIMIT_BYTES - FINAL_BITRATE_RESERVE_BYTES);
        long totalBitrate = availableBytes * 8L * 1000L / safeDurationMs;
        int targetVideoBitrate = (int) Math.max(RoundVideoEncodingOptions.HIGH_QUALITY_MIN_FINAL_BITRATE * 1024L, totalBitrate - audioBitrate);
        return Math.min(RoundVideoEncodingOptions.HIGH_QUALITY_MAX_FINAL_BITRATE * 1024, targetVideoBitrate);
    }

    public int getLiveBitrateUpdate(long currentTimestampNs) {
        if (currentTimestampNs <= 0 || currentVideoBitrate <= 0) {
            return NO_BITRATE_UPDATE;
        }
        long elapsedMs = currentTimestampNs / 1_000_000L;
        if (elapsedMs < MIN_LIVE_UPDATE_ELAPSED_MS || elapsedMs - lastLiveUpdateMs < LIVE_UPDATE_INTERVAL_MS) {
            return NO_BITRATE_UPDATE;
        }
        lastLiveUpdateMs = elapsedMs;
        if (muxedSizeEstimate <= 0) {
            return NO_BITRATE_UPDATE;
        }
        long projectedSize = muxedSizeEstimate * 60_000L / Math.max(1L, elapsedMs);
        long targetSize = RoundVideoEncodingOptions.SERVER_FILE_SIZE_LIMIT_BYTES - SERVER_LIMIT_SAFETY_BYTES;
        int minBitrate = RoundVideoEncodingOptions.HIGH_QUALITY_MIN_FINAL_BITRATE * 1024;
        if (projectedSize <= targetSize || currentVideoBitrate <= minBitrate) {
            return NO_BITRATE_UPDATE;
        }
        int targetBitrate = (int) (currentVideoBitrate * (targetSize / (float) projectedSize) * LIVE_UPDATE_SAFETY_FACTOR);
        int maxNextBitrate = Math.max(minBitrate, currentVideoBitrate - LIVE_UPDATE_MAX_STEP);
        targetBitrate = clamp(targetBitrate, minBitrate, maxNextBitrate);
        return targetBitrate < currentVideoBitrate ? targetBitrate : NO_BITRATE_UPDATE;
    }

    public int getRetryVideoBitrate(int currentBitrate, long outputSize) {
        if (outputSize <= 0) {
            return currentBitrate;
        }
        long targetBytes = RoundVideoEncodingOptions.SERVER_FILE_SIZE_LIMIT_BYTES - SERVER_LIMIT_SAFETY_BYTES;
        int minBitrate = RoundVideoEncodingOptions.HIGH_QUALITY_MIN_FINAL_BITRATE * 1024;
        if (currentBitrate <= minBitrate) {
            return minBitrate;
        }
        int scaledBitrate = (int) (currentBitrate * (targetBytes / (float) outputSize));
        return Math.max(minBitrate, scaledBitrate);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
