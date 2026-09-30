package com.wyrdly.media.domain.repository;

import com.wyrdly.media.domain.model.MediaFile;
import java.util.Optional;

public interface MediaRepository {
  MediaFile save(MediaFile mediaFile);

  Optional<MediaFile> findById(String id);
}
