/**
 * Who may publish company documents — mirrors DocumentAccess on the server. Cosmetic here (the API
 * refuses a non-publisher regardless); it decides whether the write buttons are shown at all.
 */
export function canPublishDocuments(role: string | undefined | null): boolean {
  return role === "OWNER" || role === "ADMIN" || role === "HR";
}
