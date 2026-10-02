import { Given, When, Then } from "@cucumber/cucumber";
import { expect } from "@playwright/test";
import type { CustomWorld } from "../support/world.js";

Given("que el usuario navega a la página de autenticación", async function (this: CustomWorld) {
  if (!this.page) throw new Error("Page not initialized");
  await this.page.goto(`${this.baseUrl}/login`);
  await expect(this.page.getByTestId("auth-form-card")).toBeVisible({ timeout: 10000 });
});

Given("que selecciona la pestaña de registro", async function (this: CustomWorld) {
  if (!this.page) throw new Error("Page not initialized");
  const signUpTab = this.page.getByRole("tab", { name: "Sign Up" });
  await signUpTab.click();
  await expect(this.page.getByTestId("input-fullname")).toBeVisible();
});

When(
  "completa el formulario con un nombre completo, un usuario único, un correo y una contraseña válida",
  async function (this: CustomWorld) {
    if (!this.page) throw new Error("Page not initialized");
    const uniqueId = Date.now().toString().slice(-6);
    this.currentUsername = `user_${uniqueId}`;
    this.currentEmail = `user_${uniqueId}@example.com`;

    await this.page.getByTestId("input-fullname").fill(`Test User ${uniqueId}`);
    await this.page.getByTestId("input-username").fill(this.currentUsername);
    await this.page.getByTestId("input-email").fill(this.currentEmail);
    await this.page.getByTestId("input-password").fill("PasswordSeguro123!");
    await this.page.getByTestId("input-bio").fill("E2E automated testing bio");
  }
);

When("presiona el botón de registro", async function (this: CustomWorld) {
  if (!this.page) throw new Error("Page not initialized");
  await this.page.getByTestId("auth-submit-btn").click();
});

Then("el sistema debe redirigirlo al feed principal", async function (this: CustomWorld) {
  if (!this.page) throw new Error("Page not initialized");
  await expect(this.page).toHaveURL(/.*\/feed/, { timeout: 10000 });
  await expect(this.page.getByTestId("feed-page")).toBeVisible({ timeout: 10000 });
});

Then("el usuario debe estar autenticado en la sesión", async function (this: CustomWorld) {
  if (!this.page) throw new Error("Page not initialized");
  const token = await this.page.evaluate(() => localStorage.getItem("wyrdly_token"));
  expect(token).toBeTruthy();
});

Given("que selecciona la pestaña de inicio de sesión", async function (this: CustomWorld) {
  if (!this.page) throw new Error("Page not initialized");
  const signInTab = this.page.getByRole("tab", { name: "Sign In" });
  await signInTab.click();
  await expect(this.page.getByTestId("input-username")).toBeVisible();
});

When("ingresa sus credenciales válidas", async function (this: CustomWorld) {
  if (!this.page) throw new Error("Page not initialized");
  if (!this.currentUsername) {
    const uniqueId = Date.now().toString().slice(-6);
    this.currentUsername = `user_${uniqueId}`;
    this.currentEmail = `user_${uniqueId}@example.com`;
    await this.page.request.post("http://localhost:8080/api/auth/register", {
      data: {
        fullName: `Test User ${uniqueId}`,
        username: this.currentUsername,
        email: this.currentEmail,
        password: "PasswordSeguro123!",
      },
    });
  }

  const password = "PasswordSeguro123!";
  await this.page.getByTestId("input-username").fill(this.currentUsername);
  await this.page.getByTestId("input-password").fill(password);
});

When("presiona el botón de inicio de sesión", async function (this: CustomWorld) {
  if (!this.page) throw new Error("Page not initialized");
  await this.page.getByTestId("auth-submit-btn").click();
});

When(
  "ingresa un usuario existente con una contraseña incorrecta",
  async function (this: CustomWorld) {
    if (!this.page) throw new Error("Page not initialized");
    await this.page.getByTestId("input-username").fill("yandris");
    await this.page.getByTestId("input-password").fill("ContraseñaTotalmenteErronea999!");
  }
);

Then("se debe mostrar un mensaje de error en pantalla", async function (this: CustomWorld) {
  if (!this.page) throw new Error("Page not initialized");
  await expect(this.page.getByTestId("auth-error-banner")).toBeVisible({ timeout: 8000 });
});
