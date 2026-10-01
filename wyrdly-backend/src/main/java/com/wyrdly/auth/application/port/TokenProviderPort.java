package com.wyrdly.auth.application.port;

import com.wyrdly.auth.domain.model.AuthTokens;
import com.wyrdly.auth.domain.model.User;

public interface TokenProviderPort {
  AuthTokens generateTokens(User user);

  String extractUserIdFromRefreshToken(String refreshToken);
}
