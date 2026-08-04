package com.careerpilot.auth.application.port;

/**
 * Outbound port for transactional email.
 *
 * <p>An interface in the application layer with its implementation in
 * infrastructure — the dependency inversion that keeps {@code AuthService} free
 * of any mail vendor. Three concrete benefits, none of them theoretical:
 *
 * <ul>
 *   <li>{@code AuthServiceTest} passes a no-op stub and runs in milliseconds
 *       with no SMTP server anywhere. A service that imported
 *       {@code JavaMailSender} directly could only be tested against a fake mail
 *       server or not at all.</li>
 *   <li>Switching provider — Gmail to Resend to SES — changes one adapter class
 *       and some environment variables.</li>
 *   <li>The methods speak in domain terms ("send a verification link"), not
 *       transport terms ("send a MIME message"). The service should not be
 *       assembling email bodies.</li>
 * </ul>
 *
 * <p><strong>Implementations must not throw on delivery failure.</strong> Mail
 * is a best-effort side channel: if the provider is down, registration must
 * still succeed and the user must still be able to request a resend. An
 * exception propagating from here would roll back the transaction that created
 * their account, meaning a provider outage prevents anyone from signing up.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public interface EmailSender {

    /**
     * Sends an email verification link.
     *
     * @param toAddress       recipient
     * @param recipientName   name for the greeting
     * @param verificationUrl the full link, token included
     */
    void sendVerificationEmail(String toAddress, String recipientName, String verificationUrl);

    /**
     * Sends a password reset link.
     *
     * @param toAddress     recipient
     * @param recipientName name for the greeting
     * @param resetUrl      the full link, token included
     */
    void sendPasswordResetEmail(String toAddress, String recipientName, String resetUrl);

    /**
     * Notifies a user that their password was changed.
     *
     * <p>Not a courtesy — a security control. If an attacker changes the
     * password, this is the only signal the legitimate owner receives, and the
     * only chance they have to react before losing the account entirely.
     *
     * @param toAddress     recipient
     * @param recipientName name for the greeting
     */
    void sendPasswordChangedNotification(String toAddress, String recipientName);
}
