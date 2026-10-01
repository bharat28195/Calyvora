# Calyvora — Marketing Website

A fast, dependency-free static landing site for **calyvora.in**. No build step, no framework —
just HTML, CSS, and a little vanilla JS. Deploy it to any static host in minutes.

## Files

| File | Purpose |
|------|---------|
| `index.html` | The full one-page site (hero, problem, apps, AI, moat, roadmap, contact) |
| `styles.css` | All styling (dark, AI-native brand theme) |
| `script.js` | Nav scroll state, mobile menu, scroll-reveal animations |
| `favicon.svg` | Site icon |
| `robots.txt`, `sitemap.xml` | SEO basics |

## Preview locally

Open `index.html` directly in a browser, or serve the folder:

```bash
# Python
python -m http.server 5173
# or Node
npx serve .
```

Then visit http://localhost:5173

## Before you go live — 3 quick edits

1. **Contact form.** The form posts to a placeholder. Create a free endpoint at
   [formspree.io](https://formspree.io) (or [Web3Forms](https://web3forms.com)) and replace
   `your-form-id` in `index.html`:
   ```html
   <form class="contact-form" action="https://formspree.io/f/XXXXXXXX" method="POST">
   ```
   Alternatively, delete the form and keep just the `mailto:hello@calyvora.in` link.
2. **Email address.** Set up `hello@calyvora.in` (via your domain registrar's email forwarding,
   Google Workspace, or Zoho Mail — Zoho has a free tier for custom domains).
3. **Social image (optional).** Add an `og-image.png` (1200×630) for nice link previews.

## Deploy to calyvora.in

Pick one — all are free and support your custom domain + automatic HTTPS:

### Option A — Cloudflare Pages (recommended for a `.in` domain)
1. Push this `web/` folder to a GitHub repo.
2. Cloudflare dashboard → **Workers & Pages → Create → Pages** → connect the repo.
3. Build command: *(leave blank)* · Output directory: `/` (or `web` if the repo root is the project).
4. Add custom domain `calyvora.in` — Cloudflare walks you through the DNS.

### Option B — Netlify
1. Drag-and-drop the `web/` folder onto [app.netlify.com/drop](https://app.netlify.com/drop), **or** connect the repo.
2. Site settings → **Domain management → Add custom domain** → `calyvora.in`.
3. Point your domain's DNS to Netlify (or use Netlify DNS).

### Option C — Vercel
1. `npm i -g vercel` then run `vercel` inside `web/` (framework preset: *Other*).
2. Add `calyvora.in` under the project's **Domains**.

### DNS pointing (whoever manages calyvora.in)
- **A record** `@` → host's IP, or
- **CNAME** `www` → host's target,
- follow your chosen host's exact values (they display them after you add the domain).

## Editing content

Everything is plain HTML in `index.html`. Search for a heading (e.g. "People OS") and edit the
text inline. Colors and spacing live as CSS variables at the top of `styles.css` (`--violet`,
`--aqua`, `--grad`).
