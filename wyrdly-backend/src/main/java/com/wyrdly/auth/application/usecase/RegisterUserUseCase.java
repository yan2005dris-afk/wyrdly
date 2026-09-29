package com.wyrdly.auth.application.usecase;

import com.wyrdly.auth.application.dto.AuthResponse;
import com.wyrdly.auth.application.dto.RegisterRequest;

public interface RegisterUserUseCase {
  AuthResponse register(RegisterRequest request);
}
