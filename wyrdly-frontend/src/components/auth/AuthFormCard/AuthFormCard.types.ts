import type {
  LoginCredentials,
  RegisterCredentials,
} from "../../../types/auth";

export type AuthMode = "signin" | "signup";

export interface AuthFormCardProps {
  readonly onSignIn: (credentials: LoginCredentials) => Promise<void>;
  readonly onSignUp: (credentials: RegisterCredentials) => Promise<void>;
  readonly isLoading?: boolean;
  readonly error?: string | null;
  readonly defaultMode?: AuthMode;
  readonly className?: string;
}
