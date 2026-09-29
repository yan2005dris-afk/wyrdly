import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import {
  convertToWebP,
  isRasterInputMimeType,
  supportsWebPEncoding,
  WEBP_QUALITY,
} from "./imageTransform";

describe("isRasterInputMimeType", () => {
  it("accepts png, jpeg, gif, bmp", () => {
    expect(isRasterInputMimeType("image/png")).toBe(true);
    expect(isRasterInputMimeType("image/jpeg")).toBe(true);
    expect(isRasterInputMimeType("image/gif")).toBe(true);
    expect(isRasterInputMimeType("image/bmp")).toBe(true);
  });

  it("rejects webp, mp4 and unknown types", () => {
    expect(isRasterInputMimeType("image/webp")).toBe(false);
    expect(isRasterInputMimeType("video/mp4")).toBe(false);
    expect(isRasterInputMimeType("application/octet-stream")).toBe(false);
  });
});

describe("supportsWebPEncoding", () => {
  const originalCreateElement = document.createElement.bind(document);

  afterEach(() => {
    document.createElement = originalCreateElement;
  });

  it("returns true when canvas emits a webp data url", () => {
    document.createElement = vi.fn((tag: string) => {
      const el = originalCreateElement(tag);
      if (tag === "canvas") {
        (el as HTMLCanvasElement).toDataURL = vi.fn(
          () => "data:image/webp;base64,AAAA",
        );
      }
      return el;
    }) as typeof document.createElement;
    expect(supportsWebPEncoding()).toBe(true);
  });

  it("returns false when canvas emits a non-webp data url", () => {
    document.createElement = vi.fn((tag: string) => {
      const el = originalCreateElement(tag);
      if (tag === "canvas") {
        (el as HTMLCanvasElement).toDataURL = vi.fn(
          () => "data:image/png;base64,AAAA",
        );
      }
      return el;
    }) as typeof document.createElement;
    expect(supportsWebPEncoding()).toBe(false);
  });
});

describe("convertToWebP", () => {
  beforeEach(() => {
    // Force the path that uses the detached <canvas> fallback so we
    // don't need to polyfill OffscreenCanvas in jsdom.
    if (typeof OffscreenCanvas !== "undefined") {
      // jsdom has OffscreenCanvas undefined; nothing to do.
    }
  });

  function makeFile(name: string, type: string, sizeBytes = 16): File {
    const blob = new Blob([new Uint8Array(sizeBytes)], { type });
    return new File([blob], name, { type });
  }

  function stubCanvasToBlob(blob: Blob | null): void {
    HTMLCanvasElement.prototype.toBlob = function (success) {
      if (blob) success(blob);
      else success(null);
    };
  }

  function fakeImage(width = 320, height = 240): HTMLImageElement {
    const img = document.createElement("img");
    Object.defineProperty(img, "naturalWidth", { value: width });
    Object.defineProperty(img, "naturalHeight", { value: height });
    return img;
  }

  it("rejects when input mime is not a supported raster type", async () => {
    const file = makeFile("clip.mp4", "video/mp4");
    await expect(convertToWebP(file)).rejects.toThrow(/Unsupported/);
  });

  it("rejects when the browser does not support WebP encoding", async () => {
    const originalCreateElement = document.createElement.bind(document);
    document.createElement = vi.fn((tag: string) => {
      const el = originalCreateElement(tag);
      if (tag === "canvas") {
        (el as HTMLCanvasElement).toDataURL = vi.fn(() => "data:image/png");
      }
      return el;
    }) as typeof document.createElement;

    const file = makeFile("photo.png", "image/png");
    await expect(convertToWebP(file)).rejects.toThrow(/WebP/);

    document.createElement = originalCreateElement;
  });

  it("produces a WebP File with the correct extension and mime type", async () => {
    stubCanvasToBlob(new Blob(["webp-bytes"], { type: "image/webp" }));

    const file = makeFile("vacation.png", "image/png");
    const converted = await convertToWebP(file, {
      decodeImage: async () => fakeImage(320, 240),
      rasterizeToWebP: async () =>
        new Blob(["webp-bytes"], { type: "image/webp" }),
      supportsWebPEncoding: () => true,
    });

    expect(converted.type).toBe("image/webp");
    expect(converted.name).toBe("vacation.webp");
    expect(WEBP_QUALITY).toBeCloseTo(0.8);
  });

  it("renames multi-dot filenames correctly", async () => {
    stubCanvasToBlob(new Blob(["x"], { type: "image/webp" }));
    const file = makeFile("my.summer.photo.jpeg", "image/jpeg");
    const converted = await convertToWebP(file, {
      decodeImage: async () => fakeImage(),
      rasterizeToWebP: async () => new Blob(["x"], { type: "image/webp" }),
      supportsWebPEncoding: () => true,
    });
    expect(converted.name).toBe("my.summer.photo.webp");
  });
});
