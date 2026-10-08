import { apiClient } from "./axios";

const VAPID_PUBLIC_KEY_URL = "/api/notifications/vapid-public-key";
const SUBSCRIBE_URL = "/api/notifications/subscribe";

export interface VapidPublicKeyResponse {
  readonly publicKey: string;
}

export const notificationsApi = {
  /**
   * Returns the server's VAPID public key (base64url) used as
   * `applicationServerKey` in `PushManager.subscribe`. Throws when the backend
   * returns an empty key so callers never subscribe with an invalid key.
   */
  async getVapidPublicKey(): Promise<string> {
    const response =
      await apiClient.get<VapidPublicKeyResponse>(VAPID_PUBLIC_KEY_URL);
    const publicKey = response.data?.publicKey?.trim();
    if (!publicKey) {
      throw new Error("VAPID public key was empty");
    }
    return publicKey;
  },

  async subscribe(subscription: PushSubscriptionJSON): Promise<void> {
    await apiClient.post(SUBSCRIBE_URL, subscription);
  },

  async unsubscribe(): Promise<void> {
    await apiClient.delete(SUBSCRIBE_URL);
  },
};
