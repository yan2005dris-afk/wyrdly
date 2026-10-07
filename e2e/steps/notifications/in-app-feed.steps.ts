import { Given, When, Then } from "@cucumber/cucumber";
import { expect } from "@playwright/test";
import type { CustomWorld } from "../../support/world.js";

/**
 * E2E steps for the in-app notifications feed. Hits the backend REST API directly
 * to trigger follow events, then drives the React UI to verify the bell and the
 * popover reflect the new state.
 */

const NOTIF_BTN = '[data-testid="navbar-notifications-btn"]';
const POPOVER = '[data-testid="navbar-notifications-popover"]';
const POPOVER_TESTID = '[data-testid="notification-popover"]';
const MARK_ALL_BTN = '[data-testid="mark-all-read-btn"]';

async function apiBase(this: CustomWorld): Promise<string> {
  // The backend runs on :8080 in compose, but the test world hits the frontend on the BASE_URL
  // (typically the nginx reverse proxy at :3000). The backend is reachable on the same host via
  // the /api/ prefix, so we reuse the world baseUrl.
  return this.baseUrl.replace(/\/$/, "");
}

async function authedPost(
  this: CustomWorld,
  path: string,
  body: unknown,
  token: string,
): Promise<Response> {
  const url = `${await apiBase.call(this)}${path}`;
  return fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(body),
  });
}

Given(
  "un usuario secundario {string} registrado en el sistema",
  async function (this: CustomWorld, username: string) {
    const url = `${await apiBase.call(this)}/api/auth/register`;
    const email = `${username}@wyrdly.com`;
    const password = "TestPass123!";
    const res = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        username,
        email,
        password,
        fullName: `Test ${username}`,
      }),
    });
    // 409 means the user already exists from a previous run; that's fine.
    if (!res.ok && res.status !== 409) {
      throw new Error(`Failed to register ${username}: ${res.status} ${await res.text()}`);
    }
    this.currentUsername = username;
    this.currentEmail = email;
  },
);

When(
  "el usuario {string} sigue al usuario actual",
  async function (this: CustomWorld, followerUsername: string) {
    if (!this.page) {
      throw new Error("Page not initialized");
    }
    // Recover the auth token from the page (AuthContext stores it in
    // localStorage under the key the auth feature uses).
    const token = await this.page.evaluate(() => {
      return localStorage.getItem("accessToken") || localStorage.getItem("token") || "";
    });
    if (!token) {
      throw new Error("No access token in localStorage; ensure login step ran");
    }
    // We need the current user's id (the followee). Hit the search API by
    // username to resolve it; for the E2E we can also read it from localStorage
    // if the auth feature stores the current user profile.
    const meJson = await this.page.evaluate(() => localStorage.getItem("currentUser"));
    if (!meJson) {
      throw new Error("currentUser not in localStorage");
    }
    const me = JSON.parse(meJson) as { id: string };

    const res = await authedPost.call(
      this,
      `/api/users/${me.id}/follow`,
      {},
      token,
    );
    if (!res.ok) {
      throw new Error(
        `Follow ${followerUsername} -> ${me.id} failed: ${res.status} ${await res.text()}`,
      );
    }

    // Give the backend a moment to persist + dispatch.
    await new Promise((r) => setTimeout(r, 800));
  },
);

When(
  "la campanita se vuelve a renderizar tras la siguiente consulta",
  async function (this: CustomWorld) {
    if (!this.page) {
      throw new Error("Page not initialized");
    }
    // The hook polls every 30s. To make the test deterministic, trigger a
    // manual refetch by navigating away and back, which remounts MainLayout.
    await this.page.goto(`${this.baseUrl}/explore`);
    await this.page.waitForLoadState("networkidle");
    await this.page.goto(`${this.baseUrl}/feed`);
    await this.page.waitForLoadState("networkidle");
  },
);

Then(
  "la campanita debe mostrar al menos {int} notificación no leída",
  async function (this: CustomWorld, minUnread: number) {
    if (!this.page) {
      throw new Error("Page not initialized");
    }
    const bell = this.page.locator(NOTIF_BTN);
    await expect(bell).toBeVisible();
    const badgeCount = await bell.getAttribute("data-badge-count");
    const count = badgeCount ? parseInt(badgeCount, 10) : 0;
    expect(count).toBeGreaterThanOrEqual(minUnread);
  },
);

When("abre el popover de notificaciones", async function (this: CustomWorld) {
  if (!this.page) {
    throw new Error("Page not initialized");
  }
  await this.page.locator(NOTIF_BTN).click();
  await this.page.locator(POPOVER).waitFor({ state: "visible", timeout: 5000 });
});

Then(
  "la notificación {string} del actor debe estar visible",
  async function (this: CustomWorld, type: string) {
    if (!this.page) {
      throw new Error("Page not initialized");
    }
    // The popover renders NotificationItem for each entry; each item exposes
    // data-testid="notification-item-${id}". We can verify the type badge
    // text or the count of items; for the E2E we just assert >=1 item.
    const items = this.page.locator(`${POPOVER_TESTID} [data-testid^="notification-item-"]`);
    const count = await items.count();
    expect(count).toBeGreaterThan(0);
    // The badge for type=type is data-testid="type-badge-${type}". At least
    // one of the visible items must carry the GRAPH_FOLLOW badge.
    if (type === "GRAPH_FOLLOW") {
      await expect(this.page.locator('[data-testid="type-badge-GRAPH_FOLLOW"]').first()).toBeVisible();
    }
  },
);

When("hace click en {string}", async function (this: CustomWorld, label: string) {
  if (!this.page) {
    throw new Error("Page not initialized");
  }
  const selector =
    label === "Marcar todas como leídas" ? MARK_ALL_BTN : `text=${label}`;
  await this.page.locator(selector).first().click();
});

Then(
  "el contador no leídas de la campanita debe ser {int}",
  async function (this: CustomWorld, expected: number) {
    if (!this.page) {
      throw new Error("Page not initialized");
    }
    // Give the optimistic update + the backend roundtrip a beat.
    await this.page.waitForTimeout(800);
    const bell = this.page.locator(NOTIF_BTN);
    const badgeCount = await bell.getAttribute("data-badge-count");
    const count = badgeCount ? parseInt(badgeCount, 10) : 0;
    expect(count).toBe(expected);
  },
);
