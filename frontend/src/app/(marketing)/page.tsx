import { redirect } from "next/navigation";
import { cookies } from "next/headers";

/**
 * The app's front door is the app, not a brochure.
 *
 * <p>This route used to render a marketing landing page, which meant anyone opening the deployment —
 * including a customer who just wants to work — had to find their way past it. The product story
 * lives on the separate marketing site (`website/orbit`); the application itself should open on a
 * sign-in screen, the way Keka or Zoho does.
 *
 * <p>Someone with a session goes to their dashboard; everyone else starts at log in, which links to
 * "create a workspace" for anyone who needs one.
 */
export default async function RootPage() {
  const store = await cookies();
  const signedIn = store.has("calyvora_rt") || store.has("calyvora_session");
  redirect(signedIn ? "/dashboard" : "/login");
}
