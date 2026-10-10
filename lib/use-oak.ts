"use client";
import { useEffect, useMemo, useState } from "react";
import { useLiveQuery } from "dexie-react-hooks";
import { db, ensureSeed, setMeta } from "./db";
import type { Intake } from "./domain/types";

export function useNow(ms = 30_000) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), ms);
    return () => clearInterval(t);
  }, [ms]);
  return now;
}

export function useOak() {
  useEffect(() => { void ensureSeed(); }, []);
  const profiles = useLiveQuery(() => db.profiles.filter((p) => !p.deletedAt).sortBy("createdAt"), [], []);
  const activeMeta = useLiveQuery(() => db.meta.get("activeProfile"), [], undefined);
  const activeId = (activeMeta?.value as string | undefined) ?? profiles[0]?.id;
  const profile = profiles.find((p) => p.id === activeId) ?? profiles[0];
  const routinesAll = useLiveQuery(() => (profile ? db.routines.where("clientId").equals(profile.id).toArray() : []), [profile?.id], []);
  const intakeList = useLiveQuery(() => (profile ? db.intakes.where("clientId").equals(profile.id).toArray() : []), [profile?.id], []);
  const routines = useMemo(() => routinesAll.filter((r) => !r.deletedAt).sort((a, b) => a.time.localeCompare(b.time)), [routinesAll]);
  const intakes = useMemo(() => {
    const m = new Map<string, Intake>();
    for (const i of intakeList) if (!i.deletedAt) m.set(i.key, i);
    return m;
  }, [intakeList]);
  return {
    ready: !!profile,
    profiles, profile, routines, intakes, intakeList,
    setActive: (id: string) => setMeta("activeProfile", id),
  };
}

export function useHotkey(key: string, fn: () => void) {
  useEffect(() => {
    const h = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement | null;
      if (t && (t.isContentEditable || ["INPUT", "TEXTAREA", "SELECT"].includes(t.tagName))) return;
      if (e.metaKey || e.ctrlKey || e.altKey) return;
      if (e.key.toLowerCase() === key.toLowerCase()) { e.preventDefault(); fn(); }
    };
    window.addEventListener("keydown", h);
    return () => window.removeEventListener("keydown", h);
  }, [key, fn]);
}