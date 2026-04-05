export default function Loading() {
  return (
    <div className="panel rounded-[32px] border-white/45 p-8">
      <div className="h-4 w-32 animate-pulse rounded-full bg-white/80" />
      <div className="mt-4 h-10 w-3/4 animate-pulse rounded-full bg-white/70" />
      <div className="mt-8 grid gap-4 lg:grid-cols-3">
        {Array.from({ length: 3 }).map((_, index) => (
          <div key={index} className="h-32 animate-pulse rounded-[24px] bg-white/65" />
        ))}
      </div>
    </div>
  );
}
