const ONES = ["", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve",
  "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"];
const TENS = ["", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"];

function belowHundred(n: number): string {
  if (n < 20) return ONES[n];
  return TENS[Math.floor(n / 10)] + (n % 10 ? " " + ONES[n % 10] : "");
}

function belowThousand(n: number): string {
  const h = Math.floor(n / 100);
  const rest = n % 100;
  if (!h) return belowHundred(rest);
  return ONES[h] + " Hundred" + (rest ? " and " + belowHundred(rest) : "");
}

/**
 * Whole rupees in words the Indian way — crore, lakh, thousand — as tax certificates print them:
 * 170576 → "One Lakh Seventy Thousand Five Hundred and Seventy Six Only". Paise are dropped.
 */
export function rupeesInWords(amount: number): string {
  let n = Math.floor(Math.abs(amount));
  if (n === 0) return "Zero Only";
  const parts: string[] = [];
  const crore = Math.floor(n / 10000000); n %= 10000000;
  const lakh = Math.floor(n / 100000); n %= 100000;
  const thousand = Math.floor(n / 1000); n %= 1000;
  if (crore) parts.push(belowThousand(crore) + " Crore");
  if (lakh) parts.push(belowHundred(lakh) + " Lakh");
  if (thousand) parts.push(belowHundred(thousand) + " Thousand");
  if (n) parts.push(belowThousand(n));
  return parts.join(" ") + " Only";
}
