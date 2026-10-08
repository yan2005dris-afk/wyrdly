import { create } from "zustand";

interface UiState {
  readonly isSidebarOpen: boolean;
  readonly activeModal: string | null;
  readonly openModal: (modalId: string) => void;
  readonly closeModal: () => void;
  readonly toggleSidebar: () => void;
}

export const useUiStore = create<UiState>((set) => ({
  isSidebarOpen: false,
  activeModal: null,
  openModal: (modalId: string) => set({ activeModal: modalId }),
  closeModal: () => set({ activeModal: null }),
  toggleSidebar: () =>
    set((state) => ({ isSidebarOpen: !state.isSidebarOpen })),
}));
