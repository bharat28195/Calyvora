# Priority HR Services — marketing website

A self-contained marketing site for **Priority HR Services**, the HR & payroll product.
Single file, no build step, no external dependencies.

## Preview / deploy

It's a static site — just open or serve `index.html`:

```bash
# open directly
start index.html            # Windows
# or serve locally
npx serve .                 # then visit the printed URL
python -m http.server 8000  # then http://localhost:8000
```

Deploy the folder to any static host (Netlify, Vercel, GitHub Pages, S3/CloudFront, Nginx).

## Structure

- `index.html` — the whole landing page (hero, services, modules, pricing, contact). CSS is inlined
  in `<style>`; theming uses CSS variables at `:root`. Edit the copy directly.

## Wiring to the app

- The **"Book a demo" / "Start free trial"** buttons point at `#contact` (email/phone). Change the
  `mailto:` / `tel:` links, or repoint them to your signup/app URL.
- Pricing mirrors the product: **₹100 / employee / month**, billed on active headcount.
