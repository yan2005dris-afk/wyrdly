// ==============================================================================
// V103__reset_seed_avatar_urls.cypher
// -----------------------------------------------------------------------------
// Wipe the broken localhost:9000/social-media-assets URLs that the seed script
// (neo4j/seeds/seed.cypher) inserted for dev users and posts. Those URLs were
// never reachable in prod — the frontend tried to fetch
// http://localhost:9000/social-media-assets/avatars/andy.png from
// https://wyrdly.yandro.tech and the browser refused (mixed content, CORS,
// Firefox 127+ Local Network Access). The seed files themselves have not been
// edited; this is a one-shot data fix so the existing prod graph stops serving
// dead links. Affected nodes: (:Usuario) and (:Post).
//
// The frontend already handles avatarUrl === null with a placeholder (see
// wyrdly-frontend/src/components/layout/AppNavbar/AppNavbar.tsx and the
// PostCard component). Users get a real avatar back the moment they upload
// one through POST /api/media/upload.
// ==============================================================================

// 1. User avatars pointing at the dev seed bucket.
MATCH (u:Usuario)
WHERE u.avatarUrl IS NOT NULL
  AND u.avatarUrl CONTAINS 'localhost:9000'
SET u.avatarUrl = null;

// 2. Post media pointing at the dev seed bucket.
MATCH (p:Post)
WHERE p.mediaUrl IS NOT NULL
  AND p.mediaUrl CONTAINS 'localhost:9000'
SET p.mediaUrl = null;