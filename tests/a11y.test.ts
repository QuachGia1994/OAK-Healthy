// Source-level accessibility checks for the iPhone pass (2.12.3): no DOM here, so these read the component sources.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { VI } from "../lib/i18n-vi.ts";

const src = (p: string) => readFileSync(new URL(`../${p}`, import.meta.url), "utf8");

/** Tailwind v4 `neutral` scale (node_modules/tailwindcss/theme.css, oklch(L 0 0)) converted to sRGB and rounded.
 *  DESIGN.md says neutrals are Tailwind `neutral` with no custom tokens; update this table if the palette changes. */
const NEUTRAL: Record<string, string> = { "50": "#fafafa", "300": "#d4d4d4", "400": "#a1a1a1", "500": "#737373", "600": "#525252", "700": "#404040", "800": "#262626", "900": "#171717", "950": "#0a0a0a" };
const textTone = (classes: string, dark: boolean) =>
  classes.split(/\s+/).find((c) => (dark ? /^dark:text-neutral-\d+$/ : /^text-neutral-\d+$/).test(c))?.split("-").pop();
const lum = (hex: string) => {
  const c = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255).map((v) => (v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4));
  return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
};
const contrast = (a: string, b: string) => { const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p); return (x + 0.05) / (y + 0.05); };

test("bottom nav: an inactive tab label reaches 4.5:1 in light and dark mode, also over content behind the 90% bar", () => {
  const s = src("components/app-shell.tsx");
  const body = s.slice(s.indexOf("function MobileTab"));
  // The inactive branch is the class string that carries a neutral text tone other than 900 (the active one).
  const inactive = [...body.matchAll(/"([^"]*\btext-neutral-\d+[^"]*)"/g)].map((m) => m[1]).find((c) => textTone(c, false) !== "900") ?? "";
  const light = textTone(inactive, false), dark = textTone(inactive, true);
  assert.ok(light && dark, `inactive class has a light and a dark text colour: ${inactive}`);
  // light: bg-white/90 over white, or over neutral-900 content scrolled under the bar (#e8e8e8).
  for (const bg of ["#ffffff", "#e8e8e8"]) assert.ok(contrast(NEUTRAL[light!], bg) >= 4.5, `neutral-${light} on ${bg}: ${contrast(NEUTRAL[light!], bg).toFixed(2)}:1`);
  // dark: bg-neutral-950/90 over neutral-950, or over white content scrolled under the bar (about #232323).
  for (const bg of [NEUTRAL["950"], "#232323"]) assert.ok(contrast(NEUTRAL[dark!], bg) >= 4.5, `dark neutral-${dark} on ${bg}: ${contrast(NEUTRAL[dark!], bg).toFixed(2)}:1`);
});

test("404 page: the small '404' line reaches 4.5:1 on the white / neutral-50 page and in dark mode", () => {
  const line = src("app/not-found.tsx").match(/className="([^"]*)">404</)?.[1] ?? "";
  const light = textTone(line, false), dark = textTone(line, true) ?? light;
  assert.ok(light, `404 line has a text colour: ${line}`);
  for (const bg of ["#ffffff", NEUTRAL["50"]]) assert.ok(contrast(NEUTRAL[light!], bg) >= 4.5, `neutral-${light} on ${bg}: ${contrast(NEUTRAL[light!], bg).toFixed(2)}:1`);
  assert.ok(contrast(NEUTRAL[dark!], NEUTRAL["950"]) >= 4.5, `dark neutral-${dark} on neutral-950`);
});

test("navigation: the active link carries aria-current=page (sidebar, account and bottom nav)", () => {
  const s = src("components/app-shell.tsx");
  const shell = s.slice(0, s.indexOf("function MobileTab")), tab = s.slice(s.indexOf("function MobileTab"));
  // Every sidebar <Link> styled by active(...) (NAV map + Account; not the logo) and the MobileTab link carries aria-current.
  const links = [...shell.matchAll(/<Link\b(?:=>|[^>"]|"[^"]*")*>/g)].map((m) => m[0]).filter((l) => /\bactive\(/.test(l));
  assert.ok(links.length >= 2, `sidebar links: ${links.length}`);
  for (const l of links) assert.match(l, /aria-current=\{[^}]*"page"/, l);
  assert.match(tab, /aria-current=\{[^}]*"page"/);
});

for (const [file, min] of [["components/routine-drawer.tsx", 13], ["components/settings.tsx", 2]] as const) {
  test(`${file}: every field label is tied to its control by htmlFor and a unique id`, () => {
    const s = src(file);
    const labels = [...s.matchAll(/<label className="label"([^>]*)>/g)];
    assert.ok(labels.length >= min, `labels: ${labels.length}`);
    for (const [tag, attrs] of labels) {
      const id = attrs.match(/htmlFor="([^"]+)"/)?.[1];
      assert.ok(id, `label without htmlFor: ${tag}`);
      assert.equal(s.split(`id="${id}"`).length - 1, 1, `one control with id="${id}"`);
    }
  });
}

test("routine form: the Category chips are a labelled group and the selected chip is announced (aria-pressed), not shown by colour only", () => {
  const s = src("components/routine-drawer.tsx");
  assert.match(s, /role="group" aria-labelledby="r-category"/, "the Category chips are a labelled group");
  const group = s.slice(s.indexOf('aria-labelledby="r-category"'), s.indexOf("</div>", s.indexOf('aria-labelledby="r-category"')));
  const chip = group.match(/<button\b(?:=>|[^>"]|"[^"]*")*>/)?.[0] ?? "";
  assert.match(chip, /aria-pressed=\{r\.category === c\}/, chip);
  assert.match(chip, /type="button"/, chip);
});

test("Stack list: a row opens from a real button, not a clickable <li>, and the button holds no other control", () => {
  const s = src("components/stack-manager.tsx");
  assert.equal(/<li\b[^>]*onClick=/.test(s), false, "no <li onClick>");
  const open = s.lastIndexOf("<button", s.indexOf("onClick={() => openRoutine(r.id)"));
  assert.ok(open > 0, "row button opens the routine");
  const tag = s.slice(open).match(/^<button\b(?:=>|[^>"]|"[^"]*")*>/)?.[0] ?? "";
  const inner = s.slice(open + 1, s.indexOf("</button>", open));
  assert.equal(/<(button|a|input|select|textarea)\b|<(p|div)\b/.test(inner), false, "phrasing content only, no nested control");
  // The hit area is the whole row (::after), so the keyboard focus ring is drawn on that layer too.
  assert.match(tag, /focus-visible:after:ring-2/, "focus ring matches the tap target");
  // Tailwind v4: outline-hidden keeps a transparent outline for forced-colors mode; outline-none removes it entirely.
  assert.match(tag, /\boutline-hidden\b/);
  assert.doesNotMatch(tag, /\boutline-none\b/);
  // The clash icon's name is real text, not an aria-label on an svg that may be aria-hidden.
  assert.match(inner, /<span className="sr-only">\{t\("Interaction"\)\}<\/span>/);
});

test("Stack filters: status and category toggles announce the selected one (aria-pressed) inside a labelled group", () => {
  const s = src("components/stack-manager.tsx");
  const btn = (onClick: string) => {
    const at = s.lastIndexOf("<button", s.indexOf(onClick));
    assert.ok(s.indexOf(onClick) > 0 && at > 0, `button with ${onClick}`);
    return { tag: s.slice(at).match(/^<button\b(?:=>|[^>"]|"[^"]*")*>/)?.[0] ?? "", before: s.slice(0, at) };
  };
  for (const [onClick, pressed] of [["onClick={() => setStatus(s)}", /aria-pressed=\{status === s\}/], ['onClick={() => setCat("all")}', /aria-pressed=\{cat === "all"\}/], ["onClick={() => setCat(c)}", /aria-pressed=\{cat === c\}/]] as const) {
    const { tag, before } = btn(onClick);
    assert.match(tag, pressed, tag);
    assert.match(tag, /type="button"/, tag);
    // nearest opening <div> before the button is its group
    const group = before.slice(before.lastIndexOf("<div"));
    assert.match(group, /role="group" aria-label=\{t\("(Status|Category)"\)\}/, `${onClick} group`);
  }
});

test("Stack status filter: an inactive option reaches 4.5:1 in light (white bar) and dark (neutral-900 bar)", () => {
  const s = src("components/stack-manager.tsx");
  const at = s.lastIndexOf("<button", s.indexOf("onClick={() => setStatus(s)}"));
  const tag = s.slice(at).match(/^<button\b(?:=>|[^>"]|"[^"]*")*>/)?.[0] ?? "";
  // the inactive branch of `status === s ? "…" : "…"` is the second string
  const inactive = tag.match(/status === s \? "[^"]*" : "([^"]*)"/)?.[1] ?? "";
  const light = textTone(inactive, false), dark = textTone(inactive, true);
  assert.ok(light && dark, `inactive option has a light and a dark text colour: "${inactive}"`);
  assert.ok(contrast(NEUTRAL[light!], "#ffffff") >= 4.5, `neutral-${light} on #fff: ${contrast(NEUTRAL[light!], "#ffffff").toFixed(2)}:1`);
  assert.ok(contrast(NEUTRAL[dark!], NEUTRAL["900"]) >= 4.5, `dark neutral-${dark} on #171717: ${contrast(NEUTRAL[dark!], NEUTRAL["900"]).toFixed(2)}:1`);
});

for (const [file, panel] of [["components/auth.tsx", "form"], ["components/biomarkers.tsx", "form"]] as const) {
  test(`disclosure in ${file}: the toggle says whether its panel is open (aria-expanded) and which panel (aria-controls)`, () => {
    const s = src(file);
    const on = s.indexOf("onClick={() => setOpen(!open)}");
    assert.ok(on > 0, "toggle button");
    const tag = s.slice(s.lastIndexOf("<button", on)).match(/^<button\b(?:=>|[^>"]|"[^"]*")*>/)?.[0] ?? "";
    assert.match(tag, /aria-expanded=\{open\}/, tag);
    assert.match(tag, /type="button"/, tag);
    const id = tag.match(/aria-controls=\{open \? "([^"]+)" : undefined\}/)?.[1];
    assert.ok(id, `aria-controls names the panel only while it is rendered: ${tag}`);
    assert.equal(s.split(`id="${id}"`).length - 1, 1, `one panel with id="${id}"`);
    assert.match(s, new RegExp(`\\{open && \\(\\s*<${panel}\\b[^>]*id="${id}"`), "the id is on the panel the toggle opens");
  });
}

test("settings: the sync key box is not a live region, so Show does not make a screen reader speak the key", () => {
  const box = src("components/settings.tsx").match(/<output\b[^>]*id="sync-key"[^>]*>/)?.[0] ?? "";
  assert.ok(box, "the key is shown in <output id=sync-key>");
  assert.match(box, /aria-live="off"/, box);
});

test("inputs (.input): focus keeps a transparent outline for forced-colors mode (outline-hidden, not outline-none)", () => {
  const input = src("app/globals.css").match(/^\.input \{([^}]*)\}/m)?.[1] ?? "";
  assert.match(input, /\boutline-hidden\b/, input);
  assert.doesNotMatch(input, /\boutline-none\b/, input);
});

const walk = (d: string): string[] => readdirSync(new URL(`../${d}`, import.meta.url), { withFileTypes: true })
  .flatMap((e) => (e.isDirectory() ? walk(`${d}/${e.name}`) : /\.tsx?$/.test(e.name) ? [`${d}/${e.name}`] : []));

test("i18n: the 404 page is translated, and every t(\"…\") literal in app/ and components/ has a Vietnamese string", () => {
  assert.match(src("app/not-found.tsx"), /t\("Page not found"\)/);
  const missing: string[] = [];
  for (const f of [...walk("app"), ...walk("components")]) {
    for (const m of src(f).matchAll(/\b(?:t|tt)\(\s*"((?:[^"\\]|\\.)*)"/g)) {
      const s = JSON.parse(`"${m[1]}"`) as string;
      if (!(s in VI)) missing.push(`${f}: ${s}`);
    }
  }
  assert.deepEqual(missing, []);
});

// The literal scan above cannot see t(variable) or template literals; the label maps passed to t() are checked here.
test("i18n: category names and Stack status labels have Vietnamese strings", () => {
  const lit = (f: string, re: RegExp) => [...(src(f).match(re)?.[1] ?? "").matchAll(/"([^"]+)"/g)].map((m) => m[1]);
  const cats = lit("components/routine-drawer.tsx", /const CATEGORIES[^=]*=\s*\[([^\]]*)\]/);
  const status = lit("components/stack-manager.tsx", /const STATUS_LABEL\s*=\s*\{([^}]*)\}/); // keys are bare, so these are the labels
  assert.ok(cats.length >= 6 && status.length >= 4, `${cats.length} categories, ${status.length} statuses`);
  assert.deepEqual([...cats, ...status].filter((k) => !(k in VI)), []);
});
