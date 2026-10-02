import { Given, When, Then } from "@cucumber/cucumber";
import { expect } from "@playwright/test";
import type { CustomWorld } from "../support/world.js";

Given("que existe un usuario autenticado en la sesión", async function (this: CustomWorld) {
  if (!this.page) throw new Error("Page not initialized");

  const uniqueId = Date.now().toString().slice(-6);
  const username = `poster_${uniqueId}`;
  const email = `poster_${uniqueId}@example.com`;

  await this.page.goto(`${this.baseUrl}/register`);
  await expect(this.page.getByTestId("auth-form-card")).toBeVisible({ timeout: 10000 });

  const signUpTab = this.page.getByRole("tab", { name: "Sign Up" });
  await signUpTab.click();

  await this.page.getByTestId("input-fullname").fill(`Poster User ${uniqueId}`);
  await this.page.getByTestId("input-username").fill(username);
  await this.page.getByTestId("input-email").fill(email);
  await this.page.getByTestId("input-password").fill("PasswordSeguro123!");
  await this.page.getByTestId("auth-submit-btn").click();

  await expect(this.page).toHaveURL(/.*\/feed/, { timeout: 10000 });
});

Given("se encuentra en la página de feed principal", async function (this: CustomWorld) {
  if (!this.page) throw new Error("Page not initialized");
  await expect(this.page.getByTestId("feed-page")).toBeVisible({ timeout: 10000 });
});

When(
  "escribe el contenido {string} en la caja de publicación",
  async function (this: CustomWorld, content: string) {
    if (!this.page) throw new Error("Page not initialized");
    const textarea = this.page.getByTestId("create-post-textarea");
    await textarea.fill(content);
  }
);

When("presiona el botón de publicar", async function (this: CustomWorld) {
  if (!this.page) throw new Error("Page not initialized");
  const publishBtn = this.page.getByTestId("publish-post-btn");
  await publishBtn.click();
});

Then(
  "la nueva publicación debe aparecer en el feed con el texto {string}",
  async function (this: CustomWorld, expectedContent: string) {
    if (!this.page) throw new Error("Page not initialized");
    const postContent = this.page.getByTestId("post-content").filter({ hasText: expectedContent }).first();
    await expect(postContent).toBeVisible({ timeout: 10000 });
  }
);
