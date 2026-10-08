import { Component, type ErrorInfo, type ReactNode } from "react";
import { AlertTriangle, RefreshCw, Home } from "lucide-react";
import { Button } from "../../ui/Button";

export interface ErrorBoundaryProps {
  readonly children: ReactNode;
  readonly fallback?: ReactNode;
}

interface ErrorBoundaryState {
  readonly hasError: boolean;
  readonly error: Error | null;
}

export class ErrorBoundary extends Component<
  ErrorBoundaryProps,
  ErrorBoundaryState
> {
  public override state: ErrorBoundaryState = {
    hasError: false,
    error: null,
  };

  public static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error };
  }

  public override componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    console.error(
      "Uncaught error captured by ErrorBoundary:",
      error,
      errorInfo,
    );
  }

  private handleRetry = () => {
    this.setState({ hasError: false, error: null });
  };

  private handleReload = () => {
    window.location.reload();
  };

  private handleGoHome = () => {
    window.location.href = "/";
  };

  public override render(): ReactNode {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }

      return (
        <div
          className="min-h-screen flex items-center justify-center p-6 bg-slate-950 text-slate-100"
          data-testid="error-boundary-fallback"
        >
          <div className="w-full max-w-lg bg-slate-900/90 border border-slate-800 rounded-2xl p-6 sm:p-8 text-center flex flex-col items-center shadow-xl">
            <div className="w-16 h-16 rounded-2xl bg-rose-500/10 border border-rose-500/20 text-rose-400 flex items-center justify-center mb-5">
              <AlertTriangle className="w-8 h-8" />
            </div>

            <h1 className="text-2xl font-bold text-white mb-2">
              Something went wrong
            </h1>
            <p className="text-sm text-slate-400 mb-6 leading-relaxed">
              An unexpected application error occurred. You can attempt to
              retry, reload the application, or return to the main feed.
            </p>

            {this.state.error?.message && (
              <div
                className="w-full bg-slate-950 border border-slate-800/80 rounded-lg p-3 text-xs font-mono text-rose-300/90 text-left mb-6 overflow-x-auto"
                data-testid="error-boundary-message"
              >
                {this.state.error.message}
              </div>
            )}

            <div className="flex flex-wrap items-center justify-center gap-3 w-full">
              <Button
                variant="primary"
                size="md"
                onClick={this.handleRetry}
                leftIcon={<RefreshCw className="w-4 h-4" />}
                data-testid="error-boundary-retry-btn"
              >
                Try Again
              </Button>

              <Button
                variant="outline"
                size="md"
                onClick={this.handleReload}
                className="text-slate-200 border-slate-700 hover:bg-slate-800"
                data-testid="error-boundary-reload-btn"
              >
                Reload Page
              </Button>

              <Button
                variant="ghost"
                size="md"
                onClick={this.handleGoHome}
                leftIcon={<Home className="w-4 h-4" />}
                data-testid="error-boundary-home-btn"
              >
                Home
              </Button>
            </div>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
