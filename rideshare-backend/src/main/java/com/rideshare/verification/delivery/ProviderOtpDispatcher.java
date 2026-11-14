package com.rideshare.verification.delivery;

import com.rideshare.common.enums.VerificationChannel;
import com.rideshare.config.AppProperties;
import jakarta.mail.internet.InternetAddress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Properties;

/**
 * Sends codes by SMTP (email) and Twilio's REST API (SMS), each enabled by configuration.
 * When a provider is missing and {@code app.otp.log-codes=true} (development only), the code is written
 * to the server log instead. With neither, {@link #canDeliver} is false and the API answers 503.
 */
@Slf4j
@Component
public class ProviderOtpDispatcher implements OtpDispatcher {

    private final AppProperties properties;
    private final JavaMailSenderImpl mailSender;
    private final RestClient twilio;

    public ProviderOtpDispatcher(AppProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.mailSender = properties.mail().isConfigured() ? buildMailSender(properties.mail()) : null;
        AppProperties.Sms sms = properties.sms();
        this.twilio = sms.isConfigured()
                ? restClientBuilder.clone()
                        .baseUrl("https://api.twilio.com/2010-04-01/Accounts/" + sms.twilioAccountSid())
                        .defaultHeaders(h -> h.setBasicAuth(sms.twilioAccountSid(), sms.twilioAuthToken()))
                        .build()
                : null;
        if (mailSender == null || twilio == null) {
            log.warn("Verification delivery: email={}, sms={}, dev log fallback={}",
                    mailSender != null ? "SMTP" : "not configured", twilio != null ? "Twilio" : "not configured",
                    properties.otp().logCodes());
        }
    }

    @Override
    public boolean canDeliver(VerificationChannel channel) {
        boolean provider = channel == VerificationChannel.EMAIL ? mailSender != null : twilio != null;
        return provider || properties.otp().logCodes();
    }

    @Override
    public void send(VerificationChannel channel, String destination, String recipientName, String code) {
        long minutes = Math.max(1, properties.otp().validity().toMinutes());
        if (channel == VerificationChannel.EMAIL && mailSender != null) {
            sendEmail(destination, recipientName, code, minutes);
        } else if (channel == VerificationChannel.PHONE && twilio != null) {
            sendSms(destination, code, minutes);
        } else if (properties.otp().logCodes()) {
            log.info("[DEV ONLY] {} verification code for {}: {}", channel, mask(destination), code);
        } else {
            log.error("No delivery provider configured for {} verification codes", channel);
        }
    }

    private void sendEmail(String to, String name, String code, long minutes) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mail().from());
        message.setTo(to);
        message.setSubject(code + " is your VeloceRide verification code");
        message.setText("Hi " + (name == null ? "there" : name) + ",\n\n"
                + "Your VeloceRide verification code is: " + code + "\n\n"
                + "It expires in " + minutes + " minutes. If you didn't request it, you can ignore this email.\n\n"
                + "— VeloceRide");
        mailSender.send(message);
        log.info("Verification email sent to {}", mask(to));
    }

    /**
     * Sends via Twilio's Messages API. If TWILIO_FROM_NUMBER starts with "whatsapp:" (e.g. the WhatsApp sandbox
     * number whatsapp:+14155238886) the message goes over WhatsApp, which needs the recipient prefixed too.
     */
    private void sendSms(String to, String code, long minutes) {
        String from = properties.sms().twilioFromNumber().trim();
        boolean whatsapp = from.regionMatches(true, 0, WHATSAPP_PREFIX, 0, WHATSAPP_PREFIX.length());
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", whatsapp ? WHATSAPP_PREFIX + to : to);
        form.add("From", from);
        form.add("Body", code + " is your VeloceRide verification code. It expires in " + minutes + " minutes.");
        twilio.post()
                .uri("/Messages.json")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity();
        log.info("Verification {} sent to {}", whatsapp ? "WhatsApp message" : "SMS", mask(to));
    }

    private static final String WHATSAPP_PREFIX = "whatsapp:";

    private static JavaMailSenderImpl buildMailSender(AppProperties.Mail mail) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mail.host());
        sender.setPort(mail.port());
        if (mail.username() != null && !mail.username().isBlank()) {
            sender.setUsername(mail.username());
            sender.setPassword(mail.password());
        }
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", String.valueOf(mail.username() != null && !mail.username().isBlank()));
        props.put("mail.smtp.starttls.enable", String.valueOf(mail.starttls()));
        props.put("mail.smtp.connectiontimeout", String.valueOf(Duration.ofSeconds(10).toMillis()));
        props.put("mail.smtp.timeout", String.valueOf(Duration.ofSeconds(10).toMillis()));
        try {
            new InternetAddress(mail.from(), true);
        } catch (Exception e) {
            throw new IllegalStateException("MAIL_FROM is not a valid address: " + mail.from(), e);
        }
        return sender;
    }

    /** a***@example.com / +91******3210 */
    public static String mask(String destination) {
        if (destination == null) {
            return "";
        }
        int at = destination.indexOf('@');
        if (at > 0) {
            return destination.charAt(0) + "***" + destination.substring(at);
        }
        int keep = Math.min(4, destination.length());
        String prefix = destination.startsWith("+") && destination.length() > 7 ? destination.substring(0, 3) : "";
        int hidden = Math.max(0, destination.length() - keep - prefix.length());
        return prefix + "*".repeat(hidden) + destination.substring(destination.length() - keep);
    }
}
