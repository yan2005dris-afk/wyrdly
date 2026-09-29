package com.wyrdly.media.application.usecase;

import com.wyrdly.media.application.dto.MediaUploadResponse;

public interface UploadMediaUseCase {
  MediaUploadResponse upload(String userId, byte[] fileContent);
}
