# Design

Dub.co-inspired: calm neutrals, hairline borders, dense but breathable tables, keyboard-first.
Design dials: variance 4 · motion 4 · density 5.

## Tokens

| Token | Light | Use |
| --- | --- | --- |
| `taken` | `#1F6B4D` moss | brand, Taken, primary progress |
| `due` | `#466A8D` blue | due / soon |
| `skipped` | `#9A661F` amber | skipped, low stock |
| `missed` | `#B5473F` red | missed, clashes, destructive |
| neutrals | Tailwind `neutral` | surfaces, text, borders |

Each status has a `*-soft` background (dark-mode variants in `app/globals.css`).

- Type: Geist Sans for UI, Geist Mono for times and keys; tabular numbers for stats.
- Shape: `rounded-xl` cards, `rounded-lg` controls, 1px `neutral-200` borders, almost no shadow.
- Motion: 200 ms fade-in for pages and menus, layout animation on timeline rows, spring drawer (vaul).
- Feedback: every mutation shows a sonner toast; reversible ones carry **Undo**.
- Keyboard: `⌘K` command menu, `C` create, `/` search, `Esc` close.
- Components live in `components/ui.tsx` (Ring, Sparkline, StatusBadge, PageHeader, Empty) and the `.card .btn .btn-primary .input .chip .kbd` classes.