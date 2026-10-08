import type { FC } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ArrowLeft, Home, Compass, AlertCircle } from "lucide-react";
import { useAuth } from "../features/auth";
import { Button } from "../components/ui/Button";

export const NotFoundPage: FC = () => {
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();

  return (
    <div
      className="min-h-screen flex items-center justify-center p-6 bg-slate-950 text-slate-100"
      data-testid="not-found-page"
    >
      <div className="w-full max-w-lg text-center flex flex-col items-center">
        {/* Glowing 404 Badge */}
        <div className="relative mb-6">
          <div className="w-24 h-24 rounded-3xl bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center text-indigo-400 shadow-lg shadow-indigo-500/10">
            <AlertCircle className="w-12 h-12" />
          </div>
          <span className="absolute -bottom-2 -right-2 px-2.5 py-0.5 text-xs font-bold rounded-full bg-indigo-600 text-white shadow-sm">
            404
          </span>
        </div>

        {/* Text Details */}
        <h1 className="text-3xl sm:text-4xl font-extrabold tracking-tight text-white mb-3">
          Page not found
        </h1>
        <p className="text-sm sm:text-base text-slate-400 max-w-md mb-8 leading-relaxed">
          The link you followed may be broken, or the resource has been removed
          from the federated network.
        </p>

        {/* Actions */}
        <div className="flex flex-col sm:flex-row items-center gap-3 w-full sm:w-auto">
          <Link
            to={isAuthenticated ? "/feed" : "/login"}
            className="w-full sm:w-auto"
          >
            <Button
              variant="primary"
              size="lg"
              fullWidth
              leftIcon={
                isAuthenticated ? (
                  <Home className="w-4 h-4" />
                ) : (
                  <Compass className="w-4 h-4" />
                )
              }
              data-testid="not-found-home-btn"
            >
              {isAuthenticated ? "Back to Feed" : "Sign In to Wyrdly"}
            </Button>
          </Link>

          <Button
            variant="outline"
            size="lg"
            className="w-full sm:w-auto text-slate-200 border-slate-700 hover:bg-slate-800"
            leftIcon={<ArrowLeft className="w-4 h-4" />}
            onClick={() => navigate(-1)}
            data-testid="not-found-back-btn"
          >
            Go Back
          </Button>
        </div>

        {/* Footer info */}
        <div className="mt-12 text-xs text-slate-600">
          Wyrdly Federated Network • Error 404
        </div>
      </div>
    </div>
  );
};
