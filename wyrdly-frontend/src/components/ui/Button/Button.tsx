import type { FC } from "react";
import type {
  ButtonProps,
  IconButtonProps,
  ButtonVariant,
  ButtonSize,
} from "./Button.types";
import styles from "./Button.module.css";

const VARIANT_CLASSES: Record<ButtonVariant, string> = {
  primary: styles.primary,
  secondary: styles.secondary,
  outline: styles.outline,
  ghost: styles.ghost,
  danger: styles.danger,
};

const SIZE_CLASSES: Record<ButtonSize, string> = {
  sm: styles.sizeSm,
  md: styles.sizeMd,
  lg: styles.sizeLg,
};

export const Button: FC<ButtonProps> = ({
  variant = "primary",
  size = "md",
  isLoading = false,
  leftIcon,
  rightIcon,
  fullWidth = false,
  children,
  className = "",
  disabled,
  ...restProps
}) => {
  const buttonClasses = [
    styles.button,
    VARIANT_CLASSES[variant],
    SIZE_CLASSES[size],
    fullWidth ? styles.fullWidth : "",
    className,
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <button
      className={buttonClasses}
      disabled={disabled || isLoading}
      {...restProps}
    >
      {isLoading ? (
        <span className={styles.spinner} data-testid="button-spinner" />
      ) : (
        <>
          {leftIcon && <span data-testid="button-left-icon">{leftIcon}</span>}
          {children && <span>{children}</span>}
          {rightIcon && (
            <span data-testid="button-right-icon">{rightIcon}</span>
          )}
        </>
      )}
    </button>
  );
};

export const IconButton: FC<IconButtonProps> = ({
  icon,
  ariaLabel,
  variant = "ghost",
  size = "md",
  isLoading = false,
  badgeCount,
  className = "",
  disabled,
  ...restProps
}) => {
  const buttonClasses = [
    styles.iconButton,
    VARIANT_CLASSES[variant],
    SIZE_CLASSES[size],
    className,
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <button
      className={buttonClasses}
      aria-label={ariaLabel}
      disabled={disabled || isLoading}
      {...restProps}
    >
      {isLoading ? (
        <span className={styles.spinner} data-testid="icon-button-spinner" />
      ) : (
        <>
          {icon}
          {badgeCount !== undefined && badgeCount > 0 && (
            <span className={styles.iconBadge} data-testid="icon-button-badge">
              {badgeCount > 9 ? "9+" : badgeCount}
            </span>
          )}
        </>
      )}
    </button>
  );
};
