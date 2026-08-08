package com.calyvora.email;

/**
 * One outbound transport. Implementations are stateless and take their credentials from the
 * {@link EmailSettings} handed to them per send, so the same bean serves the platform's own mailbox
 * and (later) any tenant's.
 *
 * <p>Senders <em>throw</em>. Deciding whether a failure should reach the user is
 * {@link DispatchingEmailService}'s job, not the transport's.
 */
public interface EmailSender {

    /** Which provider this sender handles. */
    EmailSettings.Provider provider();

    /**
     * @throws Exception with the provider's own wording — the diagnostic endpoint reports it verbatim,
     *                   so don't wrap it in something friendlier.
     */
    void send(EmailSettings settings, String to, String subject, String body) throws Exception;
}
