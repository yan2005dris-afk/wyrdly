package com.wyrdly.auth.application.usecase;

import com.wyrdly.auth.application.dto.AuthResponse;
import com.wyrdly.auth.application.dto.RefreshTokenRequest;

public interface RefreshTokenUseCase {
  AuthResponse refresh(RefreshTokenRequest request);
}
