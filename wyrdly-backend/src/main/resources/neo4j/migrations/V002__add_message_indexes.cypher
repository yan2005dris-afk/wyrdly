// ==============================================================================
// V002__add_message_indexes.cypher
// Índices de Performance para Queries de Mensajes
// ==============================================================================

// 1. Índice compuesto para queries de chat history (senderId + recipientId + tiempo)
CREATE INDEX message_sender_recipient_time IF NOT EXISTS
FOR (m:Mensaje) ON (m.senderId, m.recipientId, m.createdAt);

// 2. Índice para búsquedas por sender alone
CREATE INDEX message_sender_time IF NOT EXISTS
FOR (m:Mensaje) ON (m.senderId, m.createdAt);

// 3. Índice para búsquedas por recipient alone
CREATE INDEX message_recipient_time IF NOT EXISTS
FOR (m:Mensaje) ON (m.recipientId, m.createdAt);
