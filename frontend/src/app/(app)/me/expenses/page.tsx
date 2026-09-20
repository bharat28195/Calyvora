import { redirect } from "next/navigation";

/**
 * Expenses moved to /finance/expenses.
 *
 * <p>It was reachable under /me, and the left nav offered it under Finance — but the shell decides
 * the active section by path prefix, so opening it from Finance switched the whole sidebar to "Me".
 * Claiming an expense is asking to be paid back, which is Finance's business; the route now says so.
 *
 * <p>This redirect stays because the path is not only in our own links. Notification rows carry the
 * link they were written with, so every expense notification already in a customer's database points
 * here, and those have to keep working long after the last link in the source was changed.
 */
export default function MyExpensesMoved() {
  redirect("/finance/expenses");
}
