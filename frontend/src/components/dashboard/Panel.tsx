import { ReactNode } from "react";

type PanelProps = {
  title: string;
  eyebrow?: string;
  children: ReactNode;
  className?: string;
};

export function Panel({ title, eyebrow, children, className = "" }: PanelProps) {
  return (
    <section className={`surface px-5 py-5 ${className}`.trim()}>
      <div className="mb-4 space-y-1">
        {eyebrow ? (
          <p className="text-xs font-semibold uppercase tracking-[0.24em] text-slate-400">
            {eyebrow}
          </p>
        ) : null}
        <h2 className="text-lg font-semibold text-white">{title}</h2>
      </div>
      {children}
    </section>
  );
}
