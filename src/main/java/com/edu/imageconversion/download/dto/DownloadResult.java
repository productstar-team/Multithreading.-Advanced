package com.edu.imageconversion.download.dto;

public record DownloadResult(
        String url,
        String status,
        String savedPath,
        long durationMs,
        String errorMessage
) {
    public static DownloadResult success(String url, String savedPath, long durationMs) {
        return new DownloadResult(url, "success", savedPath, durationMs, null);
    }

    public static DownloadResult failed(String url, String errorMessage, long durationMs) {
        return new DownloadResult(url, "failed", null, durationMs, errorMessage);
    }
}
