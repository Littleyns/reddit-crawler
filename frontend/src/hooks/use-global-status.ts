"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { useQueryClient } from "@tanstack/react-query";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

interface GlobalStatusState {
  isCrawling: boolean;
  isProcessing: boolean;
  processingMessage: string;
}

const DEFAULT_STATUS: GlobalStatusState = {
  isCrawling: false,
  isProcessing: false,
  processingMessage: "",
};

interface GlobalStatusContextValue {
  status: GlobalStatusState;
  setCrawling: (value: boolean) => void;
  setProcessing: (value: boolean) => void;
  setProcessingMessage: (message: string) => void;
}

// ---------------------------------------------------------------------------
// Context
// ---------------------------------------------------------------------------

const GlobalStatusContext = createContext<GlobalStatusContextValue | undefined>(undefined);

export function GlobalStatusProvider({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<GlobalStatusState>(DEFAULT_STATUS);
  const queryClient = useQueryClient();
  // Track active queries in the analytics namespace to auto-toggle isProcessing
  const previousAnalyticsActiveRef = useRef<number | null>(null);

  // Auto-detect analytics data fetching activity via React Query observer
  useEffect(() => {
    const unsubscribe = queryClient.getQueryCache().subscribe((event) => {
      if (event.type !== "updated") return;

      const query = event.query;
      const keys = query.queryKey as string[];
      if (!keys.includes("analytics")) return;

      const isActive = query.getObserversCount() > 0;
      previousAnalyticsActiveRef.current = isActive ? (previousAnalyticsActiveRef.current ?? 0) + 1 : Math.max((previousAnalyticsActiveRef.current ?? 1) - 1, 0);

      setStatus((prev) => ({
        ...prev,
        isProcessing: isActive && !prev.isCrawling,
        processingMessage: isActive ? "Analyzing data..." : prev.processingMessage,
      }));
    });

    return () => unsubscribe();
  }, [queryClient]);

  const setCrawling = useCallback((value: boolean) => {
    setStatus((prev) => ({ ...prev, isCrawling: value, isProcessing: !value && prev.isProcessing ? prev.isProcessing : false }));
  }, []);

  const setProcessing = useCallback((value: boolean) => {
    setStatus((prev) => ({ ...prev, isProcessing: value }));
  }, []);

  const setProcessingMessage = useCallback((message: string) => {
    setStatus((prev) => ({ ...prev, processingMessage: message }));
  }, []);

  const ctx = useMemo<GlobalStatusContextValue>(
    () => ({ status, setCrawling, setProcessing, setProcessingMessage }),
    [status, setCrawling, setProcessing, setProcessingMessage],
  );

  return <GlobalStatusContext.Provider value={ctx}>{children}</GlobalStatusContext.Provider>;
}

// ---------------------------------------------------------------------------
// Hook
// ---------------------------------------------------------------------------

export function useGlobalStatus(): GlobalStatusContextValue {
  const ctx = useContext(GlobalStatusContext);
  if (ctx === undefined) {
    throw new Error("useGlobalStatus must be used within a GlobalStatusProvider");
  }
  return ctx;
}
