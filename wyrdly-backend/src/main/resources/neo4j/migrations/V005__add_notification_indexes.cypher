// ==============================================================================
// V005__add_notification_indexes.cypher
// Índices para el feed in-app de Notificaciones
// ==============================================================================

// 1. Constraint de unicidad por id (generado como "ntf_" + 12 hex)
CREATE CONSTRAINT notification_id_unique IF NOT EXISTS
FOR (n:Notificacion) REQUIRE n.id IS UNIQUE;

// 2. Índice compuesto (recipientUserId, createdAt DESC) para el feed ordenado
CREATE INDEX notification_recipient_created_at_index IF NOT EXISTS
FOR (n:Notificacion) ON (n.recipientUserId, n.createdAt);

// 3. Índice parcial para contar no-leídas rápido
CREATE INDEX notification_unread_index IF NOT EXISTS
FOR (n:Notificacion) ON (n.recipientUserId) WHERE n.isRead = false;