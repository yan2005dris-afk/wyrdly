import { describe, expect, it } from "vitest";
import { defaultAvatarDataUrl } from "./defaultAvatar";

describe("defaultAvatarDataUrl", () => {
  it("returns a data: URL with image/svg+xml", () => {
    const url = defaultAvatarDataUrl("usr_yandris_01", "Yandris Tech");
    expect(url.startsWith("data:image/svg+xml;utf8,")).toBe(true);
  });

  it("is deterministic for the same seed", () => {
    const a = defaultAvatarDataUrl("usr_yandris_01", "Yandris Tech");
    const b = defaultAvatarDataUrl("usr_yandris_01", "Yandris Tech");
    expect(a).toBe(b);
  });

  it("uses different gradients for different seeds", () => {
    const yandris = defaultAvatarDataUrl("usr_yandris_01", "Yandris Tech");
    const allison = defaultAvatarDataUrl("usr_allison_04", "Allison Frontend");
    // The gradient stops carry hex colors; if the seeds collided both
    // SVGs would carry the same colors.
    expect(yandris).not.toBe(allison);
  });

  it("renders the user's initials in upper case", () => {
    const url = defaultAvatarDataUrl("u1", "yandris tech");
    // URL-encoded SVG embeds the raw initials; decode just the
    // meaningful slice.
    const decoded = decodeURIComponent(url);
    expect(decoded).toContain(">YT<");
  });

  it("handles a single-word name by using its first two letters", () => {
    const url = defaultAvatarDataUrl("u1", "yandris");
    const decoded = decodeURIComponent(url);
    expect(decoded).toContain(">YA<");
  });

  it("uses ? when the name is empty or missing", () => {
    const urlEmpty = defaultAvatarDataUrl("u1", "");
    const urlUndef = defaultAvatarDataUrl("u1");
    const decodedEmpty = decodeURIComponent(urlEmpty);
    const decodedUndef = decodeURIComponent(urlUndef);
    expect(decodedEmpty).toContain(">?<");
    expect(decodedUndef).toContain(">?<");
  });

  it("encodes the SVG so the data URL is safe in HTML", () => {
    const url = defaultAvatarDataUrl("u1", "Test");
    expect(url).not.toContain('"');
    expect(url).not.toContain("<");
    expect(url).not.toContain("#");
  });

  it("places the initials centered on a 80x80 viewBox", () => {
    const url = defaultAvatarDataUrl("u1", "Allison Frontend");
    const decoded = decodeURIComponent(url);
    expect(decoded).toContain('width="80"');
    expect(decoded).toContain('height="80"');
    expect(decoded).toContain('text-anchor="middle"');
  });
});
