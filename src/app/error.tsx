"use client";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";

export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <Card variant="elevated" className="rounded-[calc(var(--radius-xl)+0.25rem)]">
      <CardHeader
        title="The dashboard hit an unexpected error."
        description={error.message || "Unknown application error."}
      >
        <p className="text-xs uppercase tracking-[0.32em] text-[var(--ds-text-muted)]">App Error</p>
      </CardHeader>
      <CardContent>
        <Button type="button" onClick={reset}>
          Retry
        </Button>
      </CardContent>
    </Card>
  );
}
