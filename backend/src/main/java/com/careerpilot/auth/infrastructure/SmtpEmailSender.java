package com.careerpilot.auth.infrastructure;

import com.careerpilot.auth.application.port.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import static com.careerpilot.config.AsyncConfig.JOB_EXECUTOR;

/**
 * SMTP adapter for {@link EmailSender}.
 *
 * <p>Provider-agnostic: this speaks plain SMTP through Spring's
 * {@link JavaMailSender}, configured entirely by {@code spring.mail.*}
 * environment variables. Gmail, Resend, Brevo, SendGrid, Mailgun, and Amazon SES
 * are all reachable without changing a line here — choosing one is a deployment
 * decision.
 *
 * <p><strong>{@code @Async}, and why that is a correctness requirement rather
 * than an optimisation.</strong> An SMTP handshake takes hundreds of
 * milliseconds on a good day and blocks until timeout on a bad one. Sending
 * inline would put that latency inside the registration request and, worse,
 * inside its transaction — holding a database connection open while waiting on a
 * third party. A provider outage would then stall connections until the pool was
 * exhausted, and an entirely functional application would stop serving requests
 * because its mail vendor was slow.
 *
 * <p><strong>Failures are logged, never thrown.</strong> Because these methods
 * run on a background thread, an exception has nowhere useful to propagate — it
 * would be swallowed by the executor regardless. Catching explicitly makes that
 * deliberate and, more importantly, makes the failure visible in the log with
 * the correlation ID attached (the executor's task decorator carries the MDC
 * across the thread boundary). Registration succeeds either way; the user can
 * request a resend.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Component
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String applicationName;

    public SmtpEmailSender(JavaMailSender mailSender,
                           @Value("${app.mail.from}") String fromAddress,
                           @Value("${spring.application.name}") String applicationName) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.applicationName = applicationName;
    }

    @Override
    @Async(JOB_EXECUTOR)
    public void sendVerificationEmail(String toAddress, String recipientName, String verificationUrl) {
        String body = """
                Hi %s,

                Welcome to CareerPilot AI. Confirm your email address to activate your account:

                %s

                This link expires in 24 hours. If you did not create an account, you can ignore
                this message — no account will be activated without this confirmation.

                — The CareerPilot AI team
                """.formatted(recipientName, verificationUrl);

        send(toAddress, "Confirm your CareerPilot AI account", body, "verification");
    }

    @Override
    @Async(JOB_EXECUTOR)
    public void sendPasswordResetEmail(String toAddress, String recipientName, String resetUrl) {
        String body = """
                Hi %s,

                We received a request to reset your CareerPilot AI password. Use this link to
                choose a new one:

                %s

                This link expires in 1 hour and can be used only once.

                If you did not request this, no action is needed — your password has not been
                changed, and this link will expire unused.

                — The CareerPilot AI team
                """.formatted(recipientName, resetUrl);

        send(toAddress, "Reset your CareerPilot AI password", body, "password-reset");
    }

    @Override
    @Async(JOB_EXECUTOR)
    public void sendPasswordChangedNotification(String toAddress, String recipientName) {
        String body = """
                Hi %s,

                Your CareerPilot AI password was just changed, and you have been signed out of
                all devices.

                If this was not you, reset your password immediately — whoever made this change
                currently has access to your account.

                — The CareerPilot AI team
                """.formatted(recipientName);

        send(toAddress, "Your CareerPilot AI password was changed", body, "password-changed");
    }

    /**
     * Sends one message, converting any failure into a log entry.
     *
     * @param toAddress   recipient
     * @param subject     message subject
     * @param body        plain-text body
     * @param messageType short label used in logs; never the recipient address
     */
    private void send(String toAddress, String subject, String body, String messageType) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toAddress);
            message.setSubject(subject);
            message.setText(body);

            mailSender.send(message);
            log.info("Sent {} email", messageType);

        } catch (MailException e) {
            // The recipient address is deliberately absent from this log line.
            // Email addresses are personal data, and an error log is one of the
            // places they most easily escape a system — into an aggregator, a
            // third-party monitoring tool, or a screenshot in a support ticket.
            log.error("Failed to send {} email via {}", messageType, applicationName, e);
        }
    }
}
