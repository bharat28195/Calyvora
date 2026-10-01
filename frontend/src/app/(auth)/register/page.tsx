import { redirect } from "next/navigation";

/**
 * Self-serve signup is closed (PD-21). Orbit workspaces are provisioned by the vendor after a trial
 * request, so there is nothing for this page to do — the backend refuses /auth/register outright, and
 * a form that always fails is worse than no form.
 *
 * It stays as a redirect rather than being deleted because the address is out in the world: it was
 * the marketing site's call to action for months, and is in emails, notes and browser histories.
 * Someone who follows an old "start free" link should land on the thing that replaced it, not a 404.
 */
export default function RegisterPage() {
  redirect("/request-trial?from=register");
}
