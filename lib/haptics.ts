"use client";
// Light vibration feedback (Android Chrome and other browsers with the Vibration API; a silent no-op elsewhere).
const PATTERN = { tap: 10, success: [12, 40, 18], warn: [30, 50, 30] } as const;

export function haptic(kind: keyof typeof PATTERN = "tap") {
  if (typeof navigator === "undefined" || typeof navigator.vibrate !== "function") return;
  if (typeof matchMedia === "function" && matchMedia("(prefers-reduced-motion: reduce)").matches) return;
  if (localStorage.getItem("oak-haptics") === "off") return;
  try { navigator.vibrate(PATTERN[kind] as number | number[]); } catch { /* blocked until user gesture */ }
}
