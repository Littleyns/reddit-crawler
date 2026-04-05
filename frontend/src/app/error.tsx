"use client";

export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <div className="panel rounded-[32px] border-white/45 p-8">
      <p className="text-xs uppercase tracking-[0.32em] text-[var(--color-muted)]">App Error</p>
      <h2 className="mt-3 text-3xl font-semibold">The dashboard hit an unexpected error.</h2>
      <p className="mt-4 max-w-2xl text-sm leading-7 text-[var(--color-muted)]">
        {error.message || "Unknown application error."}
      </p>
      <button
        type="button"
        onClick={reset}
        className="mt-6 rounded-2xl bg-[var(--color-surface-dark)] px-5 py-3 text-sm font-medium text-white"
      >
        Retry
      </button>
    </div>
  );
}
