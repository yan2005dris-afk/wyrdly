package com.wyrdly.chat.application.port;

/**
 * Puerto de validación de relaciones follow. Desacopla módulo chat del módulo user. Permite
 * múltiples implementaciones (cache, evento, async, etc).
 */
public interface FollowValidationPort {

  /**
   * Verifica si dos usuarios se siguen mutuamente.
   *
   * @param userId1 ID del primer usuario
   * @param userId2 ID del segundo usuario
   * @return true si userId1 sigue a userId2 AND userId2 sigue a userId1
   */
  boolean areMutualFollowers(String userId1, String userId2);

  /**
   * Invalida cache de relación follow cuando cambia. Llamado desde FollowUserUseCase y
   * UnfollowUserUseCase.
   *
   * @param userId1 ID del primer usuario
   * @param userId2 ID del segundo usuario
   */
  void invalidateCache(String userId1, String userId2);
}
