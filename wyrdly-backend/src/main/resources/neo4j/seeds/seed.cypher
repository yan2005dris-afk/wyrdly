// ==============================================================================
// seed.cypher
// Semillas de Datos Iniciales para el Entorno de Desarrollo (Idempotente)
// ==============================================================================

// 1. Usuarios del Equipo Wyrdly
MERGE (u1:Usuario {id: "usr_yandris_01"})
ON CREATE SET 
  u1.username = "yandris",
  u1.email = "yandris@wyrdly.social",
  u1.fullName = "Yandris Tech",
  u1.bio = "Arquitecto de Software & Tech Lead en Wyrdly",
  u1.avatarUrl = "http://localhost:9000/social-media-assets/avatars/yandris.png",
  u1.passwordHash = "$2a$12$vx53idU5DJsd81SkMYa2..3wOz78V6FE8iFlIGpfQRAi2a2HaT1sG",
  u1.createdAt = datetime("2026-09-24T12:00:00Z")
ON MATCH SET
  u1.passwordHash = "$2a$12$vx53idU5DJsd81SkMYa2..3wOz78V6FE8iFlIGpfQRAi2a2HaT1sG";

MERGE (u2:Usuario {id: "usr_gino_02"})
ON CREATE SET 
  u2.username = "gino",
  u2.email = "gino@wyrdly.social",
  u2.fullName = "Gino Backend",
  u2.bio = "Especialista en Microservicios, RustFS S3 y Web Push",
  u2.avatarUrl = "http://localhost:9000/social-media-assets/avatars/gino.png",
  u2.passwordHash = "$2a$12$vx53idU5DJsd81SkMYa2..3wOz78V6FE8iFlIGpfQRAi2a2HaT1sG",
  u2.createdAt = datetime("2026-09-24T12:05:00Z")
ON MATCH SET
  u2.passwordHash = "$2a$12$vx53idU5DJsd81SkMYa2..3wOz78V6FE8iFlIGpfQRAi2a2HaT1sG";

MERGE (u3:Usuario {id: "usr_andy_03"})
ON CREATE SET 
  u3.username = "andy",
  u3.email = "andy@wyrdly.social",
  u3.fullName = "Andy Graph",
  u3.bio = "Entusiasta de Neo4j, algoritmos de grafos y WebSockets",
  u3.avatarUrl = "http://localhost:9000/social-media-assets/avatars/andy.png",
  u3.passwordHash = "$2a$12$vx53idU5DJsd81SkMYa2..3wOz78V6FE8iFlIGpfQRAi2a2HaT1sG",
  u3.createdAt = datetime("2026-09-24T12:10:00Z")
ON MATCH SET
  u3.passwordHash = "$2a$12$vx53idU5DJsd81SkMYa2..3wOz78V6FE8iFlIGpfQRAi2a2HaT1sG";

MERGE (u4:Usuario {id: "usr_allison_04"})
ON CREATE SET 
  u4.username = "allison",
  u4.email = "allison@wyrdly.social",
  u4.fullName = "Allison Frontend",
  u4.bio = "Desarrolladora React, Vite, Tailwind CSS y UI/UX",
  u4.avatarUrl = "http://localhost:9000/social-media-assets/avatars/allison.png",
  u4.passwordHash = "$2a$12$vx53idU5DJsd81SkMYa2..3wOz78V6FE8iFlIGpfQRAi2a2HaT1sG",
  u4.createdAt = datetime("2026-09-24T12:15:00Z")
ON MATCH SET
  u4.passwordHash = "$2a$12$vx53idU5DJsd81SkMYa2..3wOz78V6FE8iFlIGpfQRAi2a2HaT1sG";

// 1b. Usuarios Adicionales (para que HU05 muestre varias sugerencias FoF)
MERGE (u5:Usuario {id: "usr_marcus_05"})
ON CREATE SET
  u5.username = "marcus",
  u5.email = "marcus@wyrdly.social",
  u5.fullName = "Marcus Rivera",
  u5.bio = "Ingeniero de datos y contribuidor de proyectos open source",
  u5.avatarUrl = "http://localhost:9000/social-media-assets/avatars/marcus.png",
  u5.passwordHash = "$2a$12$vx53idU5DJsd81SkMYa2..3wOz78V6FE8iFlIGpfQRAi2a2HaT1sG",
  u5.createdAt = datetime("2026-09-24T12:20:00Z")
ON MATCH SET
  u5.passwordHash = "$2a$12$vx53idU5DJsd81SkMYa2..3wOz78V6FE8iFlIGpfQRAi2a2HaT1sG";

MERGE (u6:Usuario {id: "usr_sofia_06"})
ON CREATE SET
  u6.username = "sofia",
  u6.email = "sofia@wyrdly.social",
  u6.fullName = "Sofía Castillo",
  u6.bio = "Diseñadora UX/UI y artista digital",
  u6.avatarUrl = "http://localhost:9000/social-media-assets/avatars/sofia.png",
  u6.passwordHash = "$2a$12$vx53idU5DJsd81SkMYa2..3wOz78V6FE8iFlIGpfQRAi2a2HaT1sG",
  u6.createdAt = datetime("2026-09-24T12:25:00Z")
ON MATCH SET
  u6.passwordHash = "$2a$12$vx53idU5DJsd81SkMYa2..3wOz78V6FE8iFlIGpfQRAi2a2HaT1sG";

MERGE (u7:Usuario {id: "usr_david_07"})
ON CREATE SET
  u7.username = "david",
  u7.email = "david@wyrdly.social",
  u7.fullName = "David Otero",
  u7.bio = "Backend dev: Go, Kubernetes y observabilidad",
  u7.avatarUrl = "http://localhost:9000/social-media-assets/avatars/david.png",
  u7.passwordHash = "$2a$12$vx53idU5DJsd81SkMYa2..3wOz78V6FE8iFlIGpfQRAi2a2HaT1sG",
  u7.createdAt = datetime("2026-09-24T12:30:00Z")
ON MATCH SET
  u7.passwordHash = "$2a$12$vx53idU5DJsd81SkMYa2..3wOz78V6FE8iFlIGpfQRAi2a2HaT1sG";

MERGE (u8:Usuario {id: "usr_elena_08"})
ON CREATE SET
  u8.username = "elena",
  u8.email = "elena@wyrdly.social",
  u8.fullName = "Elena Méndez",
  u8.bio = "ML engineer y community manager de Wyrdly",
  u8.avatarUrl = "http://localhost:9000/social-media-assets/avatars/elena.png",
  u8.passwordHash = "$2a$12$vx53idU5DJsd81SkMYa2..3wOz78V6FE8iFlIGpfQRAi2a2HaT1sG",
  u8.createdAt = datetime("2026-09-24T12:35:00Z")
ON MATCH SET
  u8.passwordHash = "$2a$12$vx53idU5DJsd81SkMYa2..3wOz78V6FE8iFlIGpfQRAi2a2HaT1sG";

// 2. Relaciones de Seguimiento ([:SIGUE])
MATCH (u1:Usuario {id: "usr_yandris_01"}), (u2:Usuario {id: "usr_gino_02"})
MERGE (u1)-[r:SIGUE]->(u2)
ON CREATE SET r.createdAt = datetime("2026-09-24T13:00:00Z");

MATCH (u1:Usuario {id: "usr_yandris_01"}), (u3:Usuario {id: "usr_andy_03"})
MERGE (u1)-[r:SIGUE]->(u3)
ON CREATE SET r.createdAt = datetime("2026-09-24T13:05:00Z");

MATCH (u2:Usuario {id: "usr_gino_02"}), (u4:Usuario {id: "usr_allison_04"})
MERGE (u2)-[r:SIGUE]->(u4)
ON CREATE SET r.createdAt = datetime("2026-09-24T13:10:00Z");

MATCH (u3:Usuario {id: "usr_andy_03"}), (u4:Usuario {id: "usr_allison_04"})
MERGE (u3)-[r:SIGUE]->(u4)
ON CREATE SET r.createdAt = datetime("2026-09-24T13:15:00Z");

MATCH (u4:Usuario {id: "usr_allison_04"}), (u1:Usuario {id: "usr_yandris_01"})
MERGE (u4)-[r:SIGUE]->(u1)
ON CREATE SET r.createdAt = datetime("2026-09-24T13:20:00Z");

// 2b. Edges adicionales para que Yandris vea varias sugerencias FoF.
// Yandris ya sigue a sofia (NO debe aparecer como sugerencia). El resto son
// candidatos ordenados por mutualCount DESC.
MATCH (u1:Usuario {id: "usr_yandris_01"}), (u6:Usuario {id: "usr_sofia_06"})
MERGE (u1)-[r:SIGUE]->(u6)
ON CREATE SET r.createdAt = datetime("2026-09-24T13:25:00Z");

MATCH (u2:Usuario {id: "usr_gino_02"}), (u5:Usuario {id: "usr_marcus_05"})
MERGE (u2)-[r:SIGUE]->(u5)
ON CREATE SET r.createdAt = datetime("2026-09-24T13:30:00Z");

MATCH (u2:Usuario {id: "usr_gino_02"}), (u8:Usuario {id: "usr_elena_08"})
MERGE (u2)-[r:SIGUE]->(u8)
ON CREATE SET r.createdAt = datetime("2026-09-24T13:35:00Z");

MATCH (u3:Usuario {id: "usr_andy_03"}), (u7:Usuario {id: "usr_david_07"})
MERGE (u3)-[r:SIGUE]->(u7)
ON CREATE SET r.createdAt = datetime("2026-09-24T13:40:00Z");

MATCH (u5:Usuario {id: "usr_marcus_05"}), (u7:Usuario {id: "usr_david_07"})
MERGE (u5)-[r:SIGUE]->(u7)
ON CREATE SET r.createdAt = datetime("2026-09-24T13:45:00Z");

MATCH (u5:Usuario {id: "usr_marcus_05"}), (u8:Usuario {id: "usr_elena_08"})
MERGE (u5)-[r:SIGUE]->(u8)
ON CREATE SET r.createdAt = datetime("2026-09-24T13:50:00Z");

MATCH (u6:Usuario {id: "usr_sofia_06"}), (u5:Usuario {id: "usr_marcus_05"})
MERGE (u6)-[r:SIGUE]->(u5)
ON CREATE SET r.createdAt = datetime("2026-09-24T13:55:00Z");

// 3. Publicaciones de Prueba ([:PUBLICA])
MERGE (p1:Post {id: "pst_001"})
ON CREATE SET 
  p1.content = "¡Bienvenidos a Wyrdly! El cluster distribuido con Neo4j y RustFS está levantado.",
  p1.mediaUrl = "http://localhost:9000/social-media-assets/posts/cluster_diagram.png",
  p1.mediaType = "image/png",
  p1.createdAt = datetime("2026-09-24T14:00:00Z");

MATCH (u1:Usuario {id: "usr_yandris_01"}), (p1:Post {id: "pst_001"})
MERGE (u1)-[pub1:PUBLICA]->(p1)
ON CREATE SET pub1.createdAt = datetime("2026-09-24T14:00:00Z");

MERGE (p2:Post {id: "pst_002"})
ON CREATE SET 
  p2.content = "Probando la reactividad de Quarkus 3.x con RESTEasy Reactive y WebSockets. ¡Vuela!",
  p2.mediaUrl = null,
  p2.mediaType = null,
  p2.createdAt = datetime("2026-09-24T14:30:00Z");

MATCH (u2:Usuario {id: "usr_gino_02"}), (p2:Post {id: "pst_002"})
MERGE (u2)-[pub2:PUBLICA]->(p2)
ON CREATE SET pub2.createdAt = datetime("2026-09-24T14:30:00Z");

// 4. Reacciones a Publicaciones ([:REACCIONA])
MATCH (u2:Usuario {id: "usr_gino_02"}), (p1:Post {id: "pst_001"})
MERGE (u2)-[reac1:REACCIONA]->(p1)
ON CREATE SET reac1.tipo = "LIKE", reac1.createdAt = datetime("2026-09-24T14:05:00Z");

MATCH (u3:Usuario {id: "usr_andy_03"}), (p1:Post {id: "pst_001"})
MERGE (u3)-[reac2:REACCIONA]->(p1)
ON CREATE SET reac2.tipo = "LOVE", reac2.createdAt = datetime("2026-09-24T14:10:00Z");

MATCH (u4:Usuario {id: "usr_allison_04"}), (p1:Post {id: "pst_001"})
MERGE (u4)-[reac3:REACCIONA]->(p1)
ON CREATE SET reac3.tipo = "CELEBRATE", reac3.createdAt = datetime("2026-09-24T14:15:00Z");

MATCH (u1:Usuario {id: "usr_yandris_01"}), (p2:Post {id: "pst_002"})
MERGE (u1)-[reac4:REACCIONA]->(p2)
ON CREATE SET reac4.tipo = "LIKE", reac4.createdAt = datetime("2026-09-24T14:35:00Z");

// 5. Mensajes de Chat ([:ENVIA] y [:DIRIGIDO_A])
MERGE (m1:Mensaje {id: "msg_001"})
ON CREATE SET 
  m1.content = "¡Hola Andy! ¿Revisaste las consultas Cypher de 2 saltos para recomendaciones?",
  m1.read = true,
  m1.createdAt = datetime("2026-09-24T15:00:00Z");

MATCH (u1:Usuario {id: "usr_yandris_01"}), (m1:Mensaje {id: "msg_001"}), (u3:Usuario {id: "usr_andy_03"})
MERGE (u1)-[env1:ENVIA]->(m1)
ON CREATE SET env1.createdAt = datetime("2026-09-24T15:00:00Z");

MERGE (m1)-[:DIRIGIDO_A]->(u3);

MERGE (m2:Mensaje {id: "msg_002"})
ON CREATE SET 
  m2.content = "¡Hola Yandris! Sí, están optimizadas con índices y agregaciones MATCH.",
  m2.read = true,
  m2.createdAt = datetime("2026-09-24T15:02:00Z");

MATCH (u3:Usuario {id: "usr_andy_03"}), (m2:Mensaje {id: "msg_002"}), (u1:Usuario {id: "usr_yandris_01"})
MERGE (u3)-[env2:ENVIA]->(m2)
ON CREATE SET env2.createdAt = datetime("2026-09-24T15:02:00Z");

MERGE (m2)-[:DIRIGIDO_A]->(u1);
