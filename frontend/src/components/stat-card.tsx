import { cn } from "@/lib/utils";

interface StatCardProps {
  label: string;
  value: string;
  icon?: "database" | "message" | "hash" | "clock" | "trending-up";
  trend?: string;
  className?: string;
}

// Minimal set of icons to avoid importing from lucide-react in a cron job
const ICONS: Record<string, React.ReactNode> = {
  database: <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="none" viewBox="0 0 24 24"><path stroke="currentColor" strokeWidth="2" d="M12 6v12m-9-6h18"/></svg>,
  message: <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="none" viewBox="0 0 24 24"><path stroke="currentColor" strokeWidth="2" d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"/></svg>,
  hash: <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="none" viewBox="0 0 24 24"><path stroke="currentColor" strokeWidth="2" d="M10 2L7 22m10-20l-3 20M4 9h16M4 15h16"/></svg>,
  clock: <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="none" viewBox="0 0 24 24"><circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="2"/><path stroke="currentColor" strokeWidth="2" d="M12 7v5l3 3"/></svg>,
  "trending-up": <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="none" viewBox="0 0 24 24"><path stroke="currentColor" strokeWidth="2" d="M23 6l-9.5 9.5-5-5L1 18"/></svg>,
};

export function StatCard({ label, value, icon = "database", trend, className = "" }: StatCardProps) {
  return (
    <div
      className={cn(
        "panel-sq-dense flex items-start justify-between p-4 transition-colors hover:bg-[var(--color-accent-soft)]",
        className,
      )}
    >
      <div>
        <span className="section-label block mb-1">{label}</span>
        <p className="text-xl font-bold text-fg-primary tabular-nums">{value}</p>
        {trend && (
          <span className="mt-0.5 block text-[10px] font-medium text-accent-primary">
            {trend}
          </span>
        )}
      </div>
      <div className="text-fg-muted opacity-60">{ICONS[icon]}</div>
    </div>
  );
}