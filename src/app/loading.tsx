import { Card } from "@/components/ui/card";

export default function Loading() {
  return (
    <Card className="rounded-[calc(var(--radius-xl)+0.25rem)] p-8">
      <div className="h-4 w-32 animate-pulse rounded-full bg-white/20" />
      <div className="mt-4 h-10 w-3/4 animate-pulse rounded-full bg-white/15" />
      <div className="mt-8 grid gap-4 lg:grid-cols-3">
        {Array.from({ length: 3 }).map((_, index) => (
          <div key={index} className="h-32 animate-pulse rounded-[var(--radius-lg)] bg-white/10" />
        ))}
      </div>
    </Card>
  );
}
