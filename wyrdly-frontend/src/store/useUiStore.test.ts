import { describe, it, expect, beforeEach } from "vitest";
import { useUiStore } from "./useUiStore";

describe("useUiStore", () => {
  beforeEach(() => {
    useUiStore.setState({ isSidebarOpen: false, activeModal: null });
  });

  it("toggles sidebar state", () => {
    expect(useUiStore.getState().isSidebarOpen).toBe(false);
    useUiStore.getState().toggleSidebar();
    expect(useUiStore.getState().isSidebarOpen).toBe(true);
    useUiStore.getState().toggleSidebar();
    expect(useUiStore.getState().isSidebarOpen).toBe(false);
  });

  it("opens and closes modals", () => {
    expect(useUiStore.getState().activeModal).toBeNull();
    useUiStore.getState().openModal("edit-profile");
    expect(useUiStore.getState().activeModal).toBe("edit-profile");
    useUiStore.getState().closeModal();
    expect(useUiStore.getState().activeModal).toBeNull();
  });
});
