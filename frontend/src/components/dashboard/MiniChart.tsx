const bars = [72, 84, 58, 93, 76, 88, 68, 97, 80, 66, 91, 74];

export function MiniChart() {
  return (
    <div className="flex h-64 items-end gap-2 rounded-[1.4rem] border border-white/5 bg-slate-950/60 p-4">
      {bars.map((bar, index) => (
        <div key={index} className="flex h-full flex-1 items-end">
          <div
            className="w-full rounded-t-full bg-gradient-to-t from-sky-500 via-slate-300 to-white opacity-90"
            style={{ height: `${bar}%` }}
          />
        </div>
      ))}
    </div>
  );
}
