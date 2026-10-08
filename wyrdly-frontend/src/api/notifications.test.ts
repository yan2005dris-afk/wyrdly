import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("./axios", () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
  },
}));

import { apiClient } from "./axios";
import { notificationsApi } from "./notifications";

const mockedGet = vi.mocked(apiClient.get);
const mockedPost = vi.mocked(apiClient.post);
const mockedDelete = vi.mocked(apiClient.delete);

describe("notificationsApi", () => {
  beforeEach(() => {
    mockedGet.mockReset();
    mockedPost.mockReset();
    mockedDelete.mockReset();
  });

  describe("getVapidPublicKey", () => {
    it("GETs /api/notifications/vapid-public-key and returns the trimmed publicKey", async () => {
      mockedGet.mockResolvedValueOnce({ data: { publicKey: "  BJzl-key_  " } });

      const key = await notificationsApi.getVapidPublicKey();

      expect(mockedGet).toHaveBeenCalledWith(
        "/api/notifications/vapid-public-key",
      );
      expect(key).toBe("BJzl-key_");
    });

    it("throws when the backend returns an empty key", async () => {
      mockedGet.mockResolvedValueOnce({ data: { publicKey: "   " } });

      await expect(notificationsApi.getVapidPublicKey()).rejects.toThrow(
        "VAPID public key was empty",
      );
    });

    it("throws when the response body has no publicKey", async () => {
      mockedGet.mockResolvedValueOnce({ data: {} });

      await expect(notificationsApi.getVapidPublicKey()).rejects.toThrow(
        "VAPID public key was empty",
      );
    });

    it("propagates HTTP errors from the API client", async () => {
      mockedGet.mockRejectedValueOnce(new Error("Network Error"));

      await expect(notificationsApi.getVapidPublicKey()).rejects.toThrow(
        "Network Error",
      );
    });
  });

  describe("subscribe", () => {
    it("POSTs the subscription JSON to /api/notifications/subscribe", async () => {
      mockedPost.mockResolvedValueOnce({ data: { status: "SUBSCRIBED" } });
      const subscription: PushSubscriptionJSON = {
        endpoint: "https://push.example.com/abc",
        keys: { p256dh: "p256dh-key", auth: "auth-key" },
      };

      await notificationsApi.subscribe(subscription);

      expect(mockedPost).toHaveBeenCalledWith(
        "/api/notifications/subscribe",
        subscription,
      );
    });
  });

  describe("unsubscribe", () => {
    it("DELETEs /api/notifications/subscribe", async () => {
      mockedDelete.mockResolvedValueOnce({ data: {} });

      await notificationsApi.unsubscribe();

      expect(mockedDelete).toHaveBeenCalledWith("/api/notifications/subscribe");
    });
  });
});
