"use client";
import type { DoseState } from "@/lib/domain/timing";

export function Logo({ size = 24 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden>
      <rect width="32" height="32" rx="9" fill="#1F6B4D" />
      <path d="M16 7c4.5 3 6.5 6.3 6.5 9.6A6.5 6.5 0 0 1 16 23a6.5 6.5 0 0 1-6.5-6.4C9.5 13.3 11.5 10 16 7Z" fill="#E5F0EA" />
      <path d="M16 12v13" stroke="#1F6B4D" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  );
}

export const STATE_STYLE: Record<DoseState, { label: string; cls: string; dot: string }> = {
  taken: { label: "Taken", cls: "border-taken/20 bg-taken-soft text-taken", dot: "bg-taken" },
  skipped: { label: "Skipped", cls: "border-skipped/20 bg-skipped-soft text-skipped", dot: "bg-skipped" },
  missed: { label: "Missed", cls: "border-missed/20 bg-missed-soft text-missed", dot: "bg-missed" },
  due: { label: "Due now", cls: "border-due/20 bg-due-soft text-due", dot: "bg-due" },
  soon: { label: "Soon", cls: "border-due/20 bg-due-soft text-due", dot: "bg-due" },
  upcoming: { label: "Upcoming", cls: "border-neutral-200 bg-neutral-50 text-neutral-500 dark:border-neutral-800 dark:bg-neutral-900", dot: "bg-neutral-300" },
};

export function StatusBadge({ state }: { state: DoseState }) {
  const s = STATE_STYLE[state];
  return <span className={`chip ${s.cls}`}><span className={`size-1.5 rounded-full ${s.dot}`} />{s.label}</span>;
}

export function Ring({ value, size = 112, stroke = 10, label }: { value: number | null; size?: number; stroke?: number; label?: string }) {
  const r = (size - stroke) / 2, c = 2 * Math.PI * r, v = value ?? 0;
  return (
    <div className="relative" style={{ width: size, height: size }}>
      <svg width={size} height={size} className="-rotate-90">
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" strokeWidth={stroke} className="stroke-neutral-100 dark:stroke-neutral-800" />
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" strokeWidth={stroke} strokeLinecap="round" stroke="#1F6B4D"
          strokeDasharray={c} strokeDashoffset={c * (1 - v)} style={{ transition: "stroke-dashoffset .6s cubic-bezier(.2,.8,.2,1)" }} />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center">
        <span className="text-2xl font-semibold tracking-tight tabular">{value == null ? "—" : `${Math.round(v * 100)}%`}</span>
        {label && <span className="text-[11px] text-neutral-500">{label}</span>}
      </div>
    </div>
  );
}

export function Sparkline({ values, height = 48 }: { values: (number | null)[]; height?: number }) {
  const w = 100, n = Math.max(values.length - 1, 1);
  const pts = values.map((v, i) => [(i / n) * w, height - 4 - (v ?? 0) * (height - 8)] as const);
  const d = pts.map(([x, y], i) => `${i ? "L" : "M"}${x.toFixed(1)},${y.toFixed(1)}`).join(" ");
  return (
    <svg viewBox={`0 0 ${w} ${height}`} preserveAspectRatio="none" className="h-12 w-full overflow-visible">
      <defs><linearGradient id="spark" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stopColor="#1F6B4D" stopOpacity=".18" /><stop offset="1" stopColor="#1F6B4D" stopOpacity="0" /></linearGradient></defs>
      <path d={`${d} L${w},${height} L0,${height} Z`} fill="url(#spark)" />
      <path d={d} fill="none" stroke="#1F6B4D" strokeWidth="1.6" vectorEffect="non-scaling-stroke" strokeLinejoin="round" />
    </svg>
  );
}

export function PageHeader({ title, sub, children }: { title: string; sub?: string; children?: React.ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
      <div><h1 className="text-2xl font-semibold tracking-tight">{title}</h1>{sub && <p className="mt-1 text-sm text-neutral-500">{sub}</p>}</div>
      {children && <div className="flex items-center gap-2">{children}</div>}
    </div>
  );
}

export function Empty({ title, sub, action }: { title: string; sub: string; action?: React.ReactNode }) {
  return (
    <div className="card grid-bg flex flex-col items-center justify-center px-6 py-14 text-center">
      <Logo size={36} />
      <p className="mt-4 font-medium">{title}</p>
      <p className="mt-1 max-w-sm text-sm text-neutral-500">{sub}</p>
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}