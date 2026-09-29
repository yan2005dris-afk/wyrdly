import type { InputHTMLAttributes, ReactNode } from "react";

export type InputVariant = "default" | "pill" | "filled";

export interface InputProps extends Omit<
  InputHTMLAttributes<HTMLInputElement>,
  "size"
> {
  readonly variant?: InputVariant;
  readonly label?: string;
  readonly leftIcon?: ReactNode;
  readonly rightElement?: ReactNode;
  readonly errorText?: string;
  readonly containerClassName?: string;
}
