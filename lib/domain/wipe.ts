// Delete everything (docs/feat/delete-everything.md). Steps are injected; lib/account.ts wires the real ones.
export type WipeStep = "circles" | "reminders" | "vault" | "account" | "device";
/** Vault id and access token, derived from the sync key before sync is turned off (not the key itself). */
export interface VaultAuth { id: string; auth: string }
/** The local progress marker (meta "wipe"), written after each finished step and kept until this device is cleared.
 * accountSent: the account DELETE went out and no answer said it failed (a lost answer may mean it was deleted). */
export interface WipeProgress { vault: VaultAuth | null; account: boolean; done: WipeStep[]; accountSent?: boolean }
export interface WipeDeps {
  /** The stored marker, or a fresh one from this device's state. */
  progress(): Promise<WipeProgress>;
  save(p: WipeProgress): Promise<void>;
  /** Asked at the account step when the UI did not know of a session: false only when the server says signed out;
   * a network or server error throws, so the step fails instead of skipping the account. */
  signedIn(): Promise<boolean>;
  /** One sync pull, so circles made on another device are known. */
  pull(): Promise<void>;
  /** Live circles this device owns (with a write token); a deleted one becomes a tombstone and leaves this list. */
  ownedCircles(): Promise<string[]>;
  deleteCircle(id: string): Promise<void>;
  disableReminders(): Promise<void>;
  syncOff(): Promise<void>;
  deleteVault(v: VaultAuth): Promise<void>;
  /** "signed-out": the server answered 401 (no session, or the account is already gone). Other failures throw. */
  deleteAccount(): Promise<"ok" | "signed-out">;
  /** One call, last: also removes the marker. */
  clearDevice(): Promise<void>;
}
export type WipeResult = { ok: true } | { ok: false; step: WipeStep; error: string };
export const SIGN_IN_FIRST = "Sign in first";

/** Pull, delete owned circles, turn off reminders, turn off sync, delete the vault, delete the account, clear this
 * device, in that order. The first failure stops the run; a retry skips the steps the marker lists as done and
 * repeats the rest, each of which is safe to repeat. Sync goes off before the vault delete, so this device cannot
 * push the vault back; the server keeps a tombstone that refuses other devices (410). */
export async function deleteEverything(d: WipeDeps, onStep: (s: WipeStep) => void = () => {}): Promise<WipeResult> {
  let step: WipeStep = "circles";
  try {
    const p = await d.progress();
    await d.save(p);
    const run = async (s: WipeStep, fn: () => Promise<void>) => {
      if (p.done.includes(s)) return;
      step = s; onStep(s);
      await fn();
      p.done = [...p.done, s];
      await d.save({ ...p });
    };
    await run("circles", async () => { await d.pull(); for (const id of await d.ownedCircles()) await d.deleteCircle(id); });
    await run("reminders", () => d.disableReminders());
    await run("vault", async () => { await d.syncOff(); if (p.vault) await d.deleteVault(p.vault); });
    await run("account", async () => {
      if (!p.account && !(await d.signedIn())) return;
      p.account = true;
      const sent = !!p.accountSent;
      if (!sent) { p.accountSent = true; await d.save({ ...p }); }
      if ((await d.deleteAccount()) === "ok" || sent) return; // 401 after a sent DELETE whose answer was lost: done
      p.accountSent = false; await d.save({ ...p }); // answered 401 before anything was deleted: sign in again
      throw new Error(SIGN_IN_FIRST);
    });
    step = "device"; onStep(step);
    await d.clearDevice();
    return { ok: true };
  } catch (e) {
    return { ok: false, step, error: e instanceof Error ? e.message : String(e) };
  }
}
