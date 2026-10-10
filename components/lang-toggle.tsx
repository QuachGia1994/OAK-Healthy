"use client";
import { Languages } from "lucide-react";
import { setLang, useLang } from "@/lib/i18n";
import { setMeta } from "@/lib/db";

export function LangToggle({ className = "" }: { className?: string }) {
  const lang = useLang();
  const next = lang === "vi" ? "en" : "vi";
  return (
    <button onClick={() => { setLang(next); void setMeta("lang", next); }} aria-label={lang === "vi" ? "Switch to English" : "Chuyển sang tiếng Việt"}
      className={`btn min-h-11 px-3 ${className}`}><Languages className="size-4" />{lang === "vi" ? "EN" : "VI"}</button>
  );
}
