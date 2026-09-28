package com.yaga.auth.application.usecase;

import com.yaga.auth.application.dto.AuthResponse;
import com.yaga.auth.application.dto.RegisterRequest;

public interface RegisterUserUseCase {
  AuthResponse register(RegisterRequest request);
}
