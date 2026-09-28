package com.yaga.auth.application.port;

import com.yaga.auth.domain.model.AuthTokens;
import com.yaga.auth.domain.model.User;

public interface TokenProviderPort {
  AuthTokens generateTokens(User user);

  String extractUserIdFromRefreshToken(String refreshToken);
}
