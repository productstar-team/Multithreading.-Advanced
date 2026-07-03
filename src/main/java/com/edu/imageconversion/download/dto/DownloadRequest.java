package com.edu.imageconversion.download.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record DownloadRequest(
        @NotEmpty(message = "URL list must not be empty")
        List<String> urls,
        String targetDir
) {}
