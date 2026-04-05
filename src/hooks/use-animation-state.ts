"use client";

import { useEffect, useState } from "react";

export function useAnimationState(delay = 0) {
  const [isVisible, setIsVisible] = useState(false);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      requestAnimationFrame(() => setIsVisible(true));
    }, delay);

    return () => window.clearTimeout(timeoutId);
  }, [delay]);

  return isVisible;
}
