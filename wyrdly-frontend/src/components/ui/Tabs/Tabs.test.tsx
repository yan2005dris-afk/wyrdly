import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { Tabs } from "./Tabs";
import type { TabItem } from "./Tabs.types";

type FeedTab = "for_you" | "latest" | "relays";

const FEED_TABS: TabItem<FeedTab>[] = [
  { id: "for_you", label: "For you (Graph Feed)" },
  { id: "latest", label: "Latest" },
  { id: "relays", label: "Relays near you", count: 14 },
];

describe("Tabs Component", () => {
  it("renders all tab labels and count badges", () => {
    const handleChange = vi.fn();
    render(
      <Tabs<FeedTab>
        items={FEED_TABS}
        activeTab="for_you"
        onChange={handleChange}
      />,
    );

    expect(screen.getByText("For you (Graph Feed)")).toBeInTheDocument();
    expect(screen.getByText("Latest")).toBeInTheDocument();
    expect(screen.getByText("Relays near you")).toBeInTheDocument();
    expect(screen.getByTestId("tab-count-relays")).toHaveTextContent("14");
  });

  it("marks active tab with aria-selected='true'", () => {
    render(
      <Tabs<FeedTab> items={FEED_TABS} activeTab="latest" onChange={vi.fn()} />,
    );

    const latestTab = screen.getByTestId("tab-latest");
    const forYouTab = screen.getByTestId("tab-for_you");

    expect(latestTab).toHaveAttribute("aria-selected", "true");
    expect(forYouTab).toHaveAttribute("aria-selected", "false");
  });

  it("triggers onChange when a non-active tab is clicked", () => {
    const handleChange = vi.fn();
    render(
      <Tabs<FeedTab>
        items={FEED_TABS}
        activeTab="for_you"
        onChange={handleChange}
      />,
    );

    const latestTab = screen.getByTestId("tab-latest");
    fireEvent.click(latestTab);

    expect(handleChange).toHaveBeenCalledTimes(1);
    expect(handleChange).toHaveBeenCalledWith("latest");
  });

  it("does not trigger onChange when tab is disabled", () => {
    const handleChange = vi.fn();
    const disabledTabs: TabItem<string>[] = [
      { id: "tab1", label: "Tab 1" },
      { id: "tab2", label: "Tab 2", disabled: true },
    ];

    render(
      <Tabs items={disabledTabs} activeTab="tab1" onChange={handleChange} />,
    );

    const tab2 = screen.getByTestId("tab-tab2");
    expect(tab2).toBeDisabled();
    fireEvent.click(tab2);
    expect(handleChange).not.toHaveBeenCalled();
  });
});
