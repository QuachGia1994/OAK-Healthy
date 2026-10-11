"use client";
import Link from "next/link";
import { useT } from "@/lib/i18n";

export default function NotFound() {
  const t = useT();
  return (<div className="py-24 text-center"><p className="font-mono text-sm text-neutral-500 dark:text-neutral-400">404</p><h1 className="mt-2 text-2xl font-semibold">{t("Page not found")}</h1><Link href="/" className="btn mt-6">{t("Back to Today")}</Link></div>);
}
