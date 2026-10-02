package com.wyrdly.chat.domain.repository;

import com.wyrdly.chat.domain.model.DirectMessage;
import java.util.List;

public interface DirectMessageRepository {
  void save(DirectMessage message);

  List<DirectMessage> findBetweenUsers(String userId1, String userId2, int skip, int limit);

  long countBetweenUsers(String userId1, String userId2);
}
