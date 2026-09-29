import { useState, type FC } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../hooks/useAuth";
import type { LoginCredentials, RegisterCredentials } from "../types/auth";
import { AuthGraphHero, AuthFormCard } from "../components/auth";

export const AuthPage: FC = () => {
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const { login, register } = useAuth();
  const navigate = useNavigate();

  const handleSignIn = async (credentials: LoginCredentials) => {
    setError(null);
    setLoading(true);
    try {
      await login(credentials);
      navigate("/feed");
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError("Error signing in. Please check your credentials.");
      }
    } finally {
      setLoading(false);
    }
  };

  const handleSignUp = async (credentials: RegisterCredentials) => {
    setError(null);
    setLoading(true);
    try {
      await register(credentials);
      navigate("/feed");
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError("Error registering account. Please try again.");
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      className="min-h-screen flex items-center justify-center p-4 lg:p-12 text-slate-100"
      style={{
        background:
          "linear-gradient(135deg, #111428 0%, #1e1b4b 40%, #312e81 70%, #4338ca 100%)",
      }}
    >
      <div className="w-full max-w-7xl grid grid-cols-1 lg:grid-cols-12 gap-8 items-center min-h-[85vh]">
        <div className="lg:col-span-7">
          <AuthGraphHero />
        </div>
        <div className="lg:col-span-5">
          <AuthFormCard
            onSignIn={handleSignIn}
            onSignUp={handleSignUp}
            isLoading={loading}
            error={error}
          />
        </div>
      </div>
    </div>
  );
};
