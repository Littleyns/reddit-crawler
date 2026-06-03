type StatCardProps = {
  label: string;
  value: string;
  delta: string;
};

export function StatCard({ label, value, delta }: StatCardProps) {
  return (
    <article className="surface flex min-h-40 flex-col justify-between px-5 py-5">
      <p className="text-sm text-slate-300">{label}</p>
      <div className="space-y-2">
        <p className="text-3xl font-semibold tracking-tight text-white">{value}</p>
        <p className="text-sm text-slate-400">{delta}</p>
      </div>
    </article>
  );
}
