package com.yaga.media.application.usecase;

import com.yaga.media.application.dto.MediaUploadResponse;

public interface UploadMediaUseCase {
  MediaUploadResponse upload(String userId, String fileName, byte[] fileContent, String mimeType);
}
