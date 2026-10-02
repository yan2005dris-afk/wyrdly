import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { AuthImage } from "./AuthImage";
import { setAccessToken } from "../../api/tokenStore";

const EXTERNAL_URL = "https://images.unsplash.com/photo-123?w=200";
const OWN_MEDIA_URL = "http://localhost:8080/api/media/abc-uuid-123";
const FALLBACK_URL = "https://example.com/fallback.png";

describe("AuthImage", () => {
  beforeEach(() => {
    setAccessToken(null);
    localStorage.clear();

    vi.restoreAllMocks();
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        blob: () => Promise.resolve(new Blob(["x"])),
      } as unknown as Response),
    );
    vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:fake");
  });

  it("renders an <img> directly for external URLs (no fetch)", () => {
    render(<AuthImage src={EXTERNAL_URL} alt="cover" className="rounded-xl" />);

    const img = screen.getByTestId("auth-image");
    expect(img).toHaveAttribute("src", EXTERNAL_URL);
    expect(img).toHaveAttribute("alt", "cover");
    expect(img).toHaveAttribute("loading", "lazy");
    expect(global.fetch).not.toHaveBeenCalled();
  });

  it("renders a default inline-SVG avatar when src is null or undefined", () => {
    const { container: c1 } = render(<AuthImage src={null} alt="" />);
    const img1 = c1.querySelector("[data-testid='auth-image']");
    expect(img1).not.toBeNull();
    expect(img1?.getAttribute("src")).toMatch(/^data:image\/svg\+xml/);

    const { container: c2 } = render(<AuthImage src={undefined} alt="" />);
    const img2 = c2.querySelector("[data-testid='auth-image']");
    expect(img2).not.toBeNull();
    expect(img2?.getAttribute("src")).toMatch(/^data:image\/svg\+xml/);
  });

  it("fetches the bytes with Authorization for /api/media URLs", async () => {
    setAccessToken("test-jwt-123");

    render(<AuthImage src={OWN_MEDIA_URL} alt="post media" />);

    await waitFor(() => {
      const img = screen.getByTestId("auth-image");
      expect(img).toHaveAttribute("src", "blob:fake");
    });

    expect(global.fetch).toHaveBeenCalledTimes(1);
    const [calledUrl, calledInit] = (global.fetch as unknown as jest.Mock).mock
      .calls[0] as [string, RequestInit];
    expect(calledUrl).toBe(OWN_MEDIA_URL);
    expect(calledInit.headers).toEqual({
      Authorization: "Bearer test-jwt-123",
    });
  });

  it("falls back to fallbackSrc when fetch fails", async () => {
    setAccessToken("test-jwt");

    vi.mocked(global.fetch).mockResolvedValueOnce({
      ok: false,
      status: 403,
    } as unknown as Response);

    render(
      <AuthImage
        src={OWN_MEDIA_URL}
        alt="private"
        fallbackSrc={FALLBACK_URL}
      />,
    );

    await waitFor(() => {
      const img = screen.getByTestId("auth-image");
      expect(img).toHaveAttribute("src", FALLBACK_URL);
    });
  });

  it("renders the default SVG when fetch fails and no fallback is supplied", async () => {
    setAccessToken("test-jwt");

    vi.mocked(global.fetch).mockResolvedValueOnce({
      ok: false,
      status: 401,
    } as unknown as Response);

    render(<AuthImage src={OWN_MEDIA_URL} alt="private" />);

    await waitFor(() => {
      const img = screen.queryByTestId("auth-image");
      expect(img).not.toBeNull();
      expect(img?.getAttribute("src")).toMatch(/^data:image\/svg\+xml/);
    });
  });

  it("does not fetch when there is no JWT in token store", () => {
    render(
      <AuthImage
        src={OWN_MEDIA_URL}
        alt="private"
        fallbackSrc={FALLBACK_URL}
      />,
    );

    expect(global.fetch).not.toHaveBeenCalled();
    const img = screen.getByTestId("auth-image");
    expect(img).toHaveAttribute("src", FALLBACK_URL);
  });

  it("switches from blob to direct src when src changes", async () => {
    setAccessToken("test-jwt");

    const { rerender } = render(<AuthImage src={OWN_MEDIA_URL} alt="a" />);
    await waitFor(() => {
      const img = screen.getByTestId("auth-image");
      expect(img.getAttribute("src")).toMatch(/^blob:/);
    });

    rerender(<AuthImage src="https://other/photo.jpg" alt="b" />);
    const img = screen.getByTestId("auth-image");
    expect(img).toHaveAttribute("src", "https://other/photo.jpg");
  });
});
