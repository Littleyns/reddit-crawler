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

export function useGlobalStatus(): GlobalStatusContextValue {
  const ctx = useContext(GlobalStatusContext);
  if (ctx === undefined) {
    throw new Error("useGlobalStatus must be used within a GlobalStatusProvider");
  }
  return ctx;
}
