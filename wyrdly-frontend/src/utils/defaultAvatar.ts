/**
 * Deterministic inline-SVG avatar generator.
 *
 * Used as a fallback when the user's profile photo can't be loaded
 * (e.g. seed.cypher still references `http://localhost:9000/...`
 * URLs that the private RustFS bucket no longer serves). The browser
 * can always render this — no network call, no missing-asset risk.
 *
 * The same `seed` (a user id, username, full name, anything stable)
 * always produces the same data URL, so React effect dedup, browser
 * caching and equality checks all work.
 */

const PALETTE = [
  ["#6366f1", "#8b5cf6"], // indigo -> violet
  ["#0ea5e9", "#3b82f6"], // sky -> blue
  ["#10b981", "#14b8a6"], // emerald -> teal
  ["#f97316", "#ef4444"], // orange -> red
  ["#ec4899", "#f43f5e"], // pink -> rose
  ["#f59e0b", "#d97706"], // amber -> darker amber
  ["#8b5cf6", "#6366f1"], // violet -> indigo
  ["#06b6d4", "#0ea5e9"], // cyan -> sky
];

/** Cheap 32-bit hash. Stable across runs. */
function hashString(input: string): number {
  let h = 2166136261;
  for (let i = 0; i < input.length; i++) {
    h ^= input.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

function initialsFrom(name: string | undefined | null): string {
  if (!name) return "?";
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "?";
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

/**
 * Returns an SVG data URL for a circle with the user's initials over
 * a two-color gradient. The colors are stable for a given seed.
 */
export function defaultAvatarDataUrl(
  seed: string,
  fullName?: string | null,
): string {
  const palette = PALETTE[hashString(seed) % PALETTE.length];
  const [from, to] = palette;
  const initials = initialsFrom(fullName);
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80" viewBox="0 0 80 80"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="${from}"/><stop offset="1" stop-color="${to}"/></linearGradient></defs><circle cx="40" cy="40" r="40" fill="url(#g)"/><text x="40" y="48" text-anchor="middle" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif" font-size="30" font-weight="600" fill="#ffffff">${initials}</text></svg>`;
  // encodeURIComponent keeps the data URL safe without bloating the size.
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`;
}
