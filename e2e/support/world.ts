import { World, setWorldConstructor, type IWorldOptions } from "@cucumber/cucumber";
import type { Browser, BrowserContext, Page } from "playwright";

export interface CustomWorldParameters {
  baseUrl?: string;
  headless?: boolean;
}

export class CustomWorld extends World<CustomWorldParameters> {
  public browser?: Browser;
  public context?: BrowserContext;
  public page?: Page;

  public baseUrl: string;
  public currentUsername?: string;
  public currentEmail?: string;

  constructor(options: IWorldOptions<CustomWorldParameters>) {
    super(options);
    this.baseUrl = process.env.BASE_URL || options.parameters?.baseUrl || "http://localhost:5173";
  }
}

setWorldConstructor(CustomWorld);
