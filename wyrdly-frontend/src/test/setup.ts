import "@testing-library/jest-dom";
import { afterEach } from "vitest";
import { cleanup } from "@testing-library/react";

// Auto-unmount every renderHook/render call between tests so that
// background listeners (BroadcastChannel, serviceWorker.message,
// visibilitychange, setAppBadge) installed by one test do not leak into
// the next one. Without this, optimistic-update tests leave hooks alive
// with cached unreadCount=2 and pollute the assertions of later tests.
afterEach(() => {
  cleanup();
});
