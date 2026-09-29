package com.wyrdly.auth.application.usecase;

import com.wyrdly.auth.application.dto.AuthResponse;
import com.wyrdly.auth.application.dto.LoginRequest;

public interface AuthenticateUserUseCase {
  AuthResponse authenticate(LoginRequest request);
}
