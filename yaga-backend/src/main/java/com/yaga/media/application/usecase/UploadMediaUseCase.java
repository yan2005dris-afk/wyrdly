package com.yaga.media.application.usecase;

import com.yaga.media.application.dto.MediaUploadResponse;

public interface UploadMediaUseCase {
  MediaUploadResponse upload(String userId, byte[] fileContent);
}
