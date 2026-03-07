# kennytv.eu

Personal website for kennytv (Nassim Jahnke). Next.js 16 App Router, React 19, TypeScript, Tailwind CSS 4, Bun. Statically exported to GitHub Pages.

## Conventions

- Try to keep pages and scripts below 250 lines and split them up into different files or components if necessary
- Path alias: `@/*` → `src/*`
- Static export (`output: 'export'` in `next.config.ts`), no server-side features
- Pages use route groups and co-located `_components`/`_lib` directories
- Python scripts generate JSON data files consumed by the frontend
- Never try reading json file contents in the asset dir

## Skills

- `main-page.md` — Home page, shared layout, converter
- `secretgraph.md` — Server usage statistics dashboard
- `entity-data.md` — Minecraft entity data browser/differ
- `packet-data.md` — Minecraft packet data browser/differ
- `scripts.md` — Python data scripts, CI/CD workflows
