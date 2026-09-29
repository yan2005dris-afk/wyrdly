import { useState, type FC, type FormEvent } from "react";
import { Eye, EyeOff, ArrowRight, Key, Wallet } from "lucide-react";
import type { AuthFormCardProps, AuthMode } from "./AuthFormCard.types";
import { Tabs } from "../../ui/Tabs";
import { Input } from "../../ui/Input";
import { Button } from "../../ui/Button";
import styles from "./AuthFormCard.module.css";

const AUTH_TABS = [
  { id: "signin" as const, label: "Sign In" },
  { id: "signup" as const, label: "Sign Up" },
];

export const AuthFormCard: FC<AuthFormCardProps> = ({
  onSignIn,
  onSignUp,
  isLoading = false,
  error = null,
  defaultMode = "signin",
  className = "",
}) => {
  const [mode, setMode] = useState<AuthMode>(defaultMode);
  const [showPassword, setShowPassword] = useState(false);

  const [formData, setFormData] = useState({
    fullName: "",
    username: "",
    email: "",
    usernameOrEmail: "",
    password: "",
    bio: "",
  });

  const isSignUp = mode === "signup";

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (isSignUp) {
      await onSignUp({
        fullName: formData.fullName,
        username: formData.username,
        email: formData.email,
        password: formData.password,
        bio: formData.bio || undefined,
      });
    } else {
      await onSignIn({
        usernameOrEmail:
          formData.usernameOrEmail || formData.username || formData.email,
        password: formData.password,
      });
    }
  };

  return (
    <div className={`${styles.card} ${className}`} data-testid="auth-form-card">
      <div className={styles.header}>
        <h3 className={styles.title}>Welcome to Wyrdly</h3>
        <p className={styles.subtitle}>
          One identity across every federated instance.
        </p>
      </div>

      {/* Mode Switcher Tabs */}
      <Tabs<AuthMode>
        items={AUTH_TABS}
        activeTab={mode}
        onChange={setMode}
        variant="pill"
        className="mb-6"
      />

      {error && (
        <div className={styles.errorBanner} data-testid="auth-error-banner">
          {error}
        </div>
      )}

      <form onSubmit={handleSubmit} className={styles.form}>
        {isSignUp ? (
          <>
            <Input
              label="Full Name"
              placeholder="Your full name"
              value={formData.fullName}
              onChange={(e) =>
                setFormData({ ...formData, fullName: e.target.value })
              }
              required
              data-testid="input-fullname"
            />

            <Input
              label="Username"
              placeholder="username"
              value={formData.username}
              onChange={(e) =>
                setFormData({ ...formData, username: e.target.value })
              }
              leftIcon={<span className="text-slate-400 font-semibold">@</span>}
              required
              data-testid="input-username"
            />

            <Input
              label="Email"
              type="email"
              placeholder="name@domain.com"
              value={formData.email}
              onChange={(e) =>
                setFormData({ ...formData, email: e.target.value })
              }
              required
              data-testid="input-email"
            />
          </>
        ) : (
          <Input
            label="Username or Email"
            placeholder="username or email"
            value={formData.usernameOrEmail || formData.username}
            onChange={(e) =>
              setFormData({
                ...formData,
                usernameOrEmail: e.target.value,
                username: e.target.value,
              })
            }
            leftIcon={<span className="text-slate-400 font-semibold">@</span>}
            required
            data-testid="input-username"
          />
        )}

        <Input
          label="Password"
          type={showPassword ? "text" : "password"}
          placeholder="••••••••"
          value={formData.password}
          onChange={(e) =>
            setFormData({ ...formData, password: e.target.value })
          }
          required
          rightElement={
            <button
              type="button"
              onClick={() => setShowPassword(!showPassword)}
              className={styles.togglePasswordBtn}
              aria-label={showPassword ? "Hide password" : "Show password"}
              data-testid="toggle-password-btn"
            >
              {showPassword ? (
                <EyeOff className="w-4 h-4" />
              ) : (
                <Eye className="w-4 h-4" />
              )}
            </button>
          }
          data-testid="input-password"
        />

        {isSignUp && (
          <Input
            label="Short Bio"
            placeholder="Tell others about you"
            value={formData.bio}
            onChange={(e) => setFormData({ ...formData, bio: e.target.value })}
            data-testid="input-bio"
          />
        )}

        <Button
          type="submit"
          variant="primary"
          size="lg"
          fullWidth
          isLoading={isLoading}
          rightIcon={<ArrowRight className="w-4 h-4" />}
          data-testid="auth-submit-btn"
        >
          {isSignUp ? "Get Started" : "Sign In"}
        </Button>

        <div className={styles.divider}>
          <div className={styles.dividerLine} />
          <span className={styles.dividerText}>or</span>
        </div>

        <div className={styles.quickOptionsGrid}>
          <Button
            type="button"
            variant="outline"
            leftIcon={<Key className="w-4 h-4 text-indigo-600" />}
          >
            Passkey
          </Button>
          <Button
            type="button"
            variant="outline"
            leftIcon={<Wallet className="w-4 h-4 text-indigo-600" />}
          >
            Wallet
          </Button>
        </div>

        <p className={styles.legalText}>
          By continuing you agree to the Relay Protocol & Distributed Terms.
        </p>
      </form>
    </div>
  );
};
