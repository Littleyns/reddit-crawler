import { tableRows } from "@/lib/data";

export function DataTable() {
  return (
    <div className="overflow-hidden rounded-[1.4rem] border border-white/5 bg-slate-950/50">
      <div className="grid grid-cols-[1.5fr_1fr_1fr_1fr_0.8fr] gap-3 border-b border-white/5 px-4 py-3 text-xs font-semibold uppercase tracking-[0.24em] text-slate-400">
        <span>Subreddit</span>
        <span>Posts</span>
        <span>Comments</span>
        <span>Sentiment</span>
        <span>Freshness</span>
      </div>
      <div className="divide-y divide-white/5">
        {tableRows.map((row) => (
          <div
            key={row.subreddit}
            className="grid grid-cols-[1.5fr_1fr_1fr_1fr_0.8fr] gap-3 px-4 py-3 text-sm text-slate-200"
          >
            <span className="font-medium text-white">{row.subreddit}</span>
            <span>{row.posts}</span>
            <span>{row.comments}</span>
            <span>{row.sentiment}</span>
            <span className="text-slate-400">{row.freshness}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
