calyvora.net — the USD site
===========================

Upload EVERY file in this folder to the public_html of calyvora.net on Hostinger.
Keep the filenames exactly as they are.

  index.html            the page (USD pricing)
  orbit.css             shared stylesheet - identical to the calyvora.in copy
  favicon.svg
  favicon.ico
  apple-touch-icon.png

Do NOT upload this README.

What differs from calyvora.in
-----------------------------
  * Pricing is $6 / $5 / Custom, minimum $49, instead of the rupee list.
  * The GST line is gone. GST is an Indian tax; the page says "excludes any sales
    tax or VAT that applies where you are" instead of naming one we may not charge.
  * No HR Services page or footer column. Priority HR Services places staff in
    India; advertising recruitment to someone we cannot staff for is a promise we
    cannot keep.
  * About and Leadership link back to calyvora.in. The company story lives in one
    place - two copies is two versions to keep true.
  * Trial links carry ?from=net-*, so a request in the console tells you which site
    it came from, and therefore which currency to approve it in.
  * canonical + hreflang tags. Without them a search engine treats one site as a
    duplicate of the other and picks a winner itself, possibly showing the wrong
    currency to the visitor.

Both sites point at the same app: orbit.calyvora.in. There is one product and one
deployment; only the price shown differs.

Mail: do NOT add any mail DNS records to calyvora.net. Mail goes out from
calyvora.in, which is the domain verified in Resend.

When you change the price
-------------------------
Two places, and they must agree:
  1. this page  (the three cards + the small print under them)
  2. the app    /platform/pricing, currency USD - that is what customers are billed
See docs/PRICING.md.
