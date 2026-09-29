package com.wyrdly.media.domain.repository;

import com.wyrdly.media.domain.model.MediaFile;

public interface MediaRepository {
  MediaFile save(MediaFile mediaFile);
}
