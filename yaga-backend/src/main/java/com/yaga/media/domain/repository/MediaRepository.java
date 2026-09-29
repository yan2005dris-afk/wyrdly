package com.yaga.media.domain.repository;

import com.yaga.media.domain.model.MediaFile;

public interface MediaRepository {
  MediaFile save(MediaFile mediaFile);
}
