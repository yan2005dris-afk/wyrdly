import { BeforeAll, AfterAll, Before, After, Status, setDefaultTimeout } from "@cucumber/cucumber";
import { chromium, type Browser } from "playwright";
import type { CustomWorld } from "./world.js";

setDefaultTimeout(30 * 1000);

let globalBrowser: Browser | null = null;

BeforeAll(async function () {
  const isHeadless = process.env.HEADLESS !== "false";
  globalBrowser = await chromium.launch({
    headless: isHeadless,
    args: ["--no-sandbox", "--disable-setuid-sandbox"],
  });
});

AfterAll(async function () {
  if (globalBrowser) {
    await globalBrowser.close();
    globalBrowser = null;
  }
});

Before(async function (this: CustomWorld) {
  if (!globalBrowser) {
    throw new Error("Browser was not initialized in BeforeAll hook");
  }

  this.browser = globalBrowser;
  this.context = await this.browser.newContext({
    viewport: { width: 1280, height: 720 },
    ignoreHTTPSErrors: true,
  });
  this.page = await this.context.newPage();
});

After(async function (this: CustomWorld, scenario) {
  if (scenario.result?.status === Status.FAILED && this.page) {
    const screenshot = await this.page.screenshot({
      fullPage: true,
    });
    this.attach(screenshot, "image/png");
  }

  if (this.page) {
    await this.page.close();
  }
  if (this.context) {
    await this.context.close();
  }
});
