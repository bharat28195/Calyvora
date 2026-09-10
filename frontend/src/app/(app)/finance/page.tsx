import { redirect } from "next/navigation";

// Finance is a section, not a screen — but the sidebar's own label is a link, and it pointed at a
// route with no page behind it. Every other top-level entry (/me, /team, /performance, /people) has
// one; this was the single exception, so clicking "Finance" answered 404 while its two children
// worked perfectly.
//
// Pay rather than My finances, because payday is the reason an employee opens this product most
// months. Landing on a summary page would be one more click to the thing they came for.
export default function FinanceIndexPage() {
  redirect("/finance/pay");
}
