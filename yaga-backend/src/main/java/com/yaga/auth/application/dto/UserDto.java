package com.yaga.auth.application.dto;

import com.yaga.auth.domain.model.User;

public record UserDto(
    String id, String username, String email, String fullName, String bio, String avatarUrl) {

  public static UserDto fromDomain(User user) {
    return new UserDto(
        user.id(), user.username(), user.email(), user.fullName(), user.bio(), user.avatarUrl());
  }
}
