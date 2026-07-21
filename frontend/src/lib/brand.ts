/**
 * Central brand config. Per the founder's direction, Calyvora is the parent company and the product
 * gets its own name. Set `product` to the chosen product name — the wordmark then shows
 * "<Product> by Calyvora" everywhere. Until a name is picked, product === parent.
 */
export const brand = {
  product: "Orbit",
  parent: "Calyvora",
};

export const brandMark = brand.product.charAt(0).toUpperCase();
export const hasParent = brand.product !== brand.parent;
