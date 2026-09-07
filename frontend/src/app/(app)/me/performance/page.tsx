import { redirect } from "next/navigation";

// Moved to /performance/me.
//
// The stub stays because notification rows already sitting in customer databases carry the old path,
// and a deep link that 404s a year after the move looks like the record itself is gone. Redirecting
// costs one file; rewriting history does not exist as an option.
export default function MovedPage() {
  redirect("/performance/me");
}
