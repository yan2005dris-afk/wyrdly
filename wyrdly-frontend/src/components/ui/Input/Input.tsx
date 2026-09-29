import { forwardRef } from "react";
import type { InputProps, InputVariant } from "./Input.types";
import styles from "./Input.module.css";

const VARIANT_CLASSES: Record<InputVariant, string> = {
  default: styles.variantDefault,
  pill: styles.variantPill,
  filled: styles.variantFilled,
};

export const Input = forwardRef<HTMLInputElement, InputProps>(
  (
    {
      variant = "default",
      label,
      leftIcon,
      rightElement,
      errorText,
      containerClassName = "",
      className = "",
      id,
      ...restProps
    },
    ref,
  ) => {
    const inputId =
      id || (label ? label.toLowerCase().replace(/\s+/g, "-") : undefined);

    const inputClasses = [
      styles.input,
      VARIANT_CLASSES[variant],
      leftIcon ? styles.hasLeftIcon : "",
      rightElement ? styles.hasRightElement : "",
      errorText ? styles.inputError : "",
      className,
    ]
      .filter(Boolean)
      .join(" ");

    return (
      <div className={`${styles.container} ${containerClassName}`}>
        {label && (
          <label htmlFor={inputId} className={styles.label}>
            {label}
          </label>
        )}
        <div className={styles.inputWrapper}>
          {leftIcon && (
            <span className={styles.leftIcon} data-testid="input-left-icon">
              {leftIcon}
            </span>
          )}
          <input
            id={inputId}
            ref={ref}
            className={inputClasses}
            aria-invalid={Boolean(errorText)}
            aria-errormessage={errorText ? `${inputId}-error` : undefined}
            {...restProps}
          />
          {rightElement && (
            <span
              className={styles.rightElement}
              data-testid="input-right-element"
            >
              {rightElement}
            </span>
          )}
        </div>
        {errorText && (
          <span
            id={`${inputId}-error`}
            className={styles.errorText}
            data-testid="input-error-text"
          >
            {errorText}
          </span>
        )}
      </div>
    );
  },
);

Input.displayName = "Input";
