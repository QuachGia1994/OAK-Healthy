// Pretty share URLs /s/<handle>/<slug>. Only these words and the item count are public; the stack stays encrypted.
export function slugify(s: string, max = 32): string {
  return s.normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/[đĐ]/g, "d").toLowerCase()
    .replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "").slice(0, max).replace(/-+$/, "");
}

export const SLUG_PART = /^[a-z0-9](?:[a-z0-9-]{0,46}[a-z0-9])?$/;
export const isPrettySlug = (handle: string, slug: string) => SLUG_PART.test(handle) && SLUG_PART.test(slug);

const ALPHA = "abcdefghjkmnpqrstuvwxyz23456789";
/** `handle/title-xxxx`: the 4-char suffix keeps two stacks with the same name apart. */
export function buildSlug(handle: string, title: string, rand: Uint8Array): string {
  const suffix = Array.from(rand.slice(0, 4), (n) => ALPHA[n % ALPHA.length]).join("");
  return `${slugify(handle, 24) || "oak"}/${slugify(title, 32) || "stack"}-${suffix}`;
}
