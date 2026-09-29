package com.yaga.auth.application.usecase;

import com.yaga.auth.application.dto.AuthResponse;
import com.yaga.auth.application.dto.RefreshTokenRequest;

public interface RefreshTokenUseCase {
  AuthResponse refresh(RefreshTokenRequest request);
}
