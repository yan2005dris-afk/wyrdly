package com.yaga.auth.domain.repository;

import com.yaga.auth.domain.model.User;
import java.util.Optional;

public interface UserRepository {
  Optional<User> findById(String id);

  Optional<User> findByUsername(String username);

  Optional<User> findByEmail(String email);

  Optional<User> findByUsernameOrEmail(String identifier);

  boolean existsByUsername(String username);

  boolean existsByEmail(String email);

  User save(User user);
}
