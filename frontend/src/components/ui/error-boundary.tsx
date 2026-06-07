"use client";

import { Component, ErrorInfo, ReactNode } from "react";

interface Props {
  fallback?: ReactNode;
  children: ReactNode;
  onError?: (error: Error, info: ErrorInfo) => void;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

/**
 * Panel-level error boundary component.
 * Catches rendering errors within a subtree and shows a
 * graceful recovery UI instead of crashing the widget.
 */
export class ErrorBoundary extends Component<Props, State> {
  public state: State = { hasError: false, error: null };

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error("[ErrorBoundary]", error.message, info.componentStack);
    this.props.onError?.(error, info);
  }

  public reset = () => {
    this.setState({ hasError: false, error: null });
  };

  render() {
    if (this.state.hasError) {
      const fb = this.props.fallback;
      if (fb) return <>{fb}</>;

      return (
        <div className="flex flex-col items-center justify-center gap-3 rounded-[24px] border border-red-500/20 bg-red-500/5 p-6">
          <p className="text-xs uppercase tracking-[0.32em] text-red-400">Panel Error</p>
          <h3 className="text-lg font-medium text-red-300">Something went wrong</h3>
          <p className="max-w-md break-all text-xs text-fg-muted">{this.state.error?.message}</p>
          <button
            type="button"
            onClick={this.reset}
            className="rounded-xl bg-red-500/20 px-4 py-2 text-sm font-medium text-red-300 hover:bg-red-500/30"
          >
            Retry panel
          </button>
        </div>
      );
    }

    return this.props.children;
  }
}

/**
 * Page-level error boundary that wraps all children with a full-page recovery screen.
 */
export function PageErrorBoundary({ children }: { children: ReactNode }) {
  return (
    <ErrorBoundary
      fallback={
        <div className="flex h-full items-center justify-center">
          <div className="panel w-[90vw] max-w-[540px] rounded-[32px] border-yellow-500/25 p-10 text-center">
            <p className="text-xs uppercase tracking-[0.32em] text-yellow-400">App Error</p>
            <h2 className="mt-3 text-xl font-semibold">Something went wrong with this page.</h2>
            <button
              type="button"
              onClick={() => window.location.reload()}
              className="mt-5 rounded-2xl bg-surface-dark px-6 py-3 text-sm font-medium text-white hover:bg-surface-high"
            >
              Reload page
            </button>
          </div>
        </div>
      }
    >
      {children}
    </ErrorBoundary>
  );
}

export default ErrorBoundary;
