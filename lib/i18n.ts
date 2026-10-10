"use client";
import { useEffect, useState, useSyncExternalStore } from "react";
import { VI } from "./i18n-vi";

export type Lang = "en" | "vi";
const KEY = "oak-lang";
const subs = new Set<() => void>();
let cur: Lang | null = null;

export function getLang(): Lang {
  if (cur) return cur;
  if (typeof window === "undefined") return "en";
  const s = localStorage.getItem(KEY);
  cur = s === "vi" || s === "en" ? s : navigator.language?.toLowerCase().startsWith("vi") ? "vi" : "en";
  return cur;
}
export function setLang(l: Lang) {
  cur = l;
  localStorage.setItem(KEY, l);
  document.documentElement.lang = l;
  subs.forEach((f) => f());
}
const subscribe = (f: () => void) => { subs.add(f); return () => subs.delete(f); };

/** Translate an English source string; {name} placeholders are filled from vars. Missing keys fall back to English. */
export function tr(lang: Lang, s: string, vars?: Record<string, string | number>) {
  let out = lang === "vi" ? (VI[s] ?? s) : s;
  if (vars) for (const [k, v] of Object.entries(vars)) out = out.split(`{${k}}`).join(String(v));
  return out;
}
export const t = (s: string, vars?: Record<string, string | number>) => tr(getLang(), s, vars);

let hydrated = false;
/** First client render matches the static HTML (English); the saved language applies right after mount. */
export function useLang(): Lang {
  const lang = useSyncExternalStore(subscribe, getLang, (): Lang => "en");
  const [mounted, setMounted] = useState(hydrated);
  useEffect(() => { hydrated = true; setMounted(true); }, []);
  return mounted ? lang : "en";
}
export function useT() {
  const lang = useLang();
  return (s: string, vars?: Record<string, string | number>) => tr(lang, s, vars);
}
export const locale = () => (getLang() === "vi" ? "vi-VN" : undefined);
