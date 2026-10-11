// Save a Blob as a file download (JSON backup, history CSV; docs/feat/history-csv.md § Format).
// The anchor sits in the page only for the click (Firefox) and is removed right after, also when click() throws; the
// blob URL is freed only later: revoking it in the same tick as click() can leave Safari/Firefox with an empty or failed download.

/** How long the blob URL stays valid after the click. */
export const REVOKE_AFTER_MS = 60_000;

/** The browser parts saveBlob uses; tests pass a fake. */
export interface DownloadEnv {
  document: { createElement(tag: "a"): HTMLAnchorElement; body: { appendChild(node: Node): unknown } };
  URL: { createObjectURL(blob: Blob): string; revokeObjectURL(url: string): void };
  setTimeout(run: () => void, ms: number): unknown;
}

const browser = (): DownloadEnv => ({ document, URL, setTimeout: (run, ms) => window.setTimeout(run, ms) });

export function saveBlob(blob: Blob, name: string, env: DownloadEnv = browser()): void {
  const href = env.URL.createObjectURL(blob);
  const a = Object.assign(env.document.createElement("a"), { href, download: name });
  env.document.body.appendChild(a);
  try {
    a.click();
  } finally {
    a.remove();
    env.setTimeout(() => env.URL.revokeObjectURL(href), REVOKE_AFTER_MS);
  }
}
