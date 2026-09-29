package com.yaga.auth.application.usecase;

import com.yaga.auth.application.dto.AuthResponse;
import com.yaga.auth.application.dto.LoginRequest;

public interface AuthenticateUserUseCase {
  AuthResponse authenticate(LoginRequest request);
}
