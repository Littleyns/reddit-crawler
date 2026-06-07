"use client";
export const dynamic = "force-dynamic";

import { BarChart3 } from "lucide-react";

const COLORS = ["#0ea5e9", "#8b5cf6", "#f43f5e"] as const;

export default function AnalyticsPage() {
  return (
    <div className="flex w-full flex-col gap-3 p-4">
      <span className="section-label block mb-1">Analytics Deep-Dive</span>
      <div className="grid grid-cols-4 gap-3">
        {[{ label: "Keywords", value: "156" }, { label: "Posts", value: "1204" }, { label: "Topics", value: "12" }, { label: "Ideas", value: "7" }].map(s => (
          <div key={s.label} className="panel-inset rounded-md p-3"><span className="text-xs text-fg-muted">{s.label}</span><span className={`text-xl font-bold`}>{s.value}</span></div>
        ))}
      </div>
    </div>
  );
}
