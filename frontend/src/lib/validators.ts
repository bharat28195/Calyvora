import { z } from "zod";

// Client-side schemas (UX). The server re-validates with Bean Validation (authority) — Sprint1 §12.

const email = z.string().trim().toLowerCase().email("Enter a valid email address").max(255);

const password = z
  .string()
  .min(10, "At least 10 characters")
  .regex(/[A-Za-z]/, "Include at least one letter")
  .regex(/[0-9]/, "Include at least one number");

const name = (label: string) =>
  z.string().trim().min(1, `${label} is required`).max(80);

export const registerSchema = z.object({
  companyName: z.string().trim().min(2, "Company name is too short").max(120),
  firstName: name("First name"),
  lastName: name("Last name"),
  email,
  password,
});
export type RegisterInput = z.infer<typeof registerSchema>;

/**
 * The public "request a free trial" form (PD-21). Notice what isn't here: a password. Nothing this
 * form submits creates an account, so there is no credential to set — the vendor provisions the
 * workspace on approval and hands the sign-in details over then.
 *
 * Only the three fields a human needs to act on the enquiry are required. Asking for a phone number
 * or a headcount before someone has decided they're interested loses more people than it qualifies.
 */
export const trialRequestSchema = z.object({
  companyName: z.string().trim().min(2, "Company name is too short").max(200),
  contactName: z.string().trim().min(2, "Please tell us your name").max(200),
  email,
  phone: z.string().trim().max(40).optional(),
  teamSize: z.string().trim().max(40).optional(),
  note: z.string().trim().max(2000).optional(),
});
export type TrialRequestFormInput = z.infer<typeof trialRequestSchema>;

/**
 * Setting a new password from a reset code. The password rule is the same object the signup form
 * uses — a second way in that accepted weaker passwords would quietly become the real rule.
 */
export const resetPasswordSchema = z.object({
  email,
  code: z.string().trim().regex(/^\d{6}$/, "The code is 6 digits"),
  newPassword: password,
});
export type ResetPasswordInput = z.infer<typeof resetPasswordSchema>;

export const loginSchema = z.object({
  email,
  password: z.string().min(1, "Password is required"),
});
export type LoginInput = z.infer<typeof loginSchema>;

export const inviteSchema = z.object({
  email,
  role: z.enum(["ADMIN", "HR", "MANAGER", "MEMBER"], { message: "Choose a role" }),
});
export type InviteInput = z.infer<typeof inviteSchema>;

export const acceptInviteSchema = z.object({
  firstName: name("First name"),
  lastName: name("Last name"),
  password,
});
export type AcceptInviteInput = z.infer<typeof acceptInviteSchema>;

export const settingsSchema = z.object({
  timezone: z.string().min(1, "Timezone is required").max(64),
  locale: z.enum(["en", "en-GB", "fr", "de", "es", "hi"]),
  currency: z.enum(["INR", "USD", "EUR", "GBP", "AED", "SGD", "AUD", "CAD"]),
  logoUrl: z
    .union([z.string().url("Must be a valid https URL").max(500), z.literal("")])
    .optional(),
});
export type SettingsInput = z.infer<typeof settingsSchema>;

/** Simple password strength estimate for the register form meter (0–4). */
export function passwordStrength(pw: string): number {
  let score = 0;
  if (pw.length >= 10) score++;
  if (pw.length >= 14) score++;
  if (/[A-Z]/.test(pw) && /[a-z]/.test(pw)) score++;
  if (/[0-9]/.test(pw) && /[^A-Za-z0-9]/.test(pw)) score++;
  return Math.min(score, 4);
}
