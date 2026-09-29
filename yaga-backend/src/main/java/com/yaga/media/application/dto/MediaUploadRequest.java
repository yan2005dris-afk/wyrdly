package com.yaga.media.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record MediaUploadRequest(
    @NotBlank(message = "fileName must not be blank") String fileName,
    @Positive(message = "fileSizeBytes must be positive") long fileSizeBytes,
    @NotBlank(message = "mimeType must not be blank") String mimeType) {}
