import { World, setWorldConstructor, type IWorldOptions } from "@cucumber/cucumber";
import type { Browser, BrowserContext, Page } from "playwright";

export interface CustomWorldParameters {
  baseUrl?: string;
  headless?: boolean;
}

export interface UserContextInfo {
  id: string;
  username: string;
  email: string;
  token?: string;
}

export class CustomWorld extends World<CustomWorldParameters> {
  public browser?: Browser;
  public context?: BrowserContext;
  public page?: Page;

  public baseUrl: string;
  public currentUsername?: string;
  public currentEmail?: string;
  public lastCreatedPostId?: string;
  public userContexts: Map<string, UserContextInfo> = new Map();
  public lastReceivedPush?: { title: string; body: string; data?: any };

  constructor(options: IWorldOptions<CustomWorldParameters>) {
    super(options);
    this.baseUrl = process.env.BASE_URL || options.parameters?.baseUrl || "http://localhost:3000";
  }
}

setWorldConstructor(CustomWorld);
