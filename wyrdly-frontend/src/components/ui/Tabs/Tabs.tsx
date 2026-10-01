import type { ReactElement } from "react";
import type { TabsProps, TabsVariant } from "./Tabs.types";
import styles from "./Tabs.module.css";

export const Tabs = <T extends string = string>({
  items,
  activeTab,
  onChange,
  variant = "underline",
  className = "",
  ariaLabel = "Tabs",
}: TabsProps<T>): ReactElement => {
  const isPill = variant === "pill";
  const containerClass = [
    isPill ? styles.pillContainer : styles.underlineContainer,
    className,
  ]
    .filter(Boolean)
    .join(" ");

  const getItemClass = (
    isSelected: boolean,
    itemVariant: TabsVariant,
  ): string => {
    if (itemVariant === "pill") {
      return [
        styles.tabButton,
        styles.pillItem,
        isSelected ? styles.pillActive : "",
      ]
        .filter(Boolean)
        .join(" ");
    }
    return [
      styles.tabButton,
      styles.underlineItem,
      isSelected ? styles.underlineActive : "",
    ]
      .filter(Boolean)
      .join(" ");
  };

  return (
    <div
      role="tablist"
      aria-label={ariaLabel}
      className={containerClass}
      data-testid="tabs-container"
    >
      {items.map((item) => {
        const isSelected = item.id === activeTab;
        return (
          <button
            key={item.id}
            role="tab"
            type="button"
            aria-selected={isSelected}
            disabled={item.disabled}
            tabIndex={isSelected ? 0 : -1}
            onClick={() => onChange(item.id)}
            className={getItemClass(isSelected, variant)}
            data-testid={`tab-${item.id}`}
          >
            {item.icon && <span>{item.icon}</span>}
            <span>{item.label}</span>
            {item.count !== undefined && (
              <span
                className={styles.tabCount}
                data-testid={`tab-count-${item.id}`}
              >
                {item.count}
              </span>
            )}
          </button>
        );
      })}
    </div>
  );
};
