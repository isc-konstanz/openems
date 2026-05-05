package io.openems.backend.mailer.smtp;

import static java.util.stream.Collectors.toUnmodifiableMap;

import java.time.ZonedDateTime;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;

import io.openems.backend.common.metadata.Mailer;

@Component(
    name = "Mailer.Smtp",
    service = Mailer.class,
    configurationPolicy = ConfigurationPolicy.REQUIRE,
    immediate = true
)
@Designate(ocd = Config.class)
public class SmtpMailer implements Mailer {

    private final Logger log = LoggerFactory.getLogger(SmtpMailer.class);

    private Session session;
    private ExecutorService executor;
    private String from;

    @Activate
    void activate(Config config) {
        this.from = config.fromAddress();

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", String.valueOf(config.useTls()));
        props.put("mail.smtp.host", config.smtpHost());
        props.put("mail.smtp.port", String.valueOf(config.smtpPort()));

        this.session = Session.getInstance(props,
            new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(
                        config.username(),
                        config.password()
                    );
                }
            });

        this.executor = Executors.newFixedThreadPool(2);
    }

    @Deactivate
    void deactivate() {
        this.executor.shutdown();
    }

    @Override
    public void sendMail(ZonedDateTime sendAt, String template, JsonElement params) {
        executor.submit(() -> {
            try {
                // Wait until scheduled time
                if (sendAt != null && sendAt.isAfter(ZonedDateTime.now())) {
                    long delay = Duration.between(ZonedDateTime.now(), sendAt).toMillis();
                    Thread.sleep(delay);
                }

                MimeMessage message = new MimeMessage(session);

                message.setFrom(new InternetAddress(from));
                message.setRecipients(
                    Message.RecipientType.TO,
                    InternetAddress.parse(extractRecipient(params))
                );

                message.setSubject(extractSubject(template, params));
                message.setContent(renderTemplate(template, params), "text/html; charset=utf-8");

                Transport.send(message);

                log.info("Mail sent successfully.");
            } catch (Exception e) {
                log.error("Failed to send mail", e);
            }
        });
    }

    private String extractRecipient(JsonElement params) {
        return params.getAsJsonObject().get("email").getAsString();
    }

    private String extractSubject(String template, JsonElement params) {
        return "Notification: " + template;
    }

    private String renderTemplate(String template, JsonElement params) {
        return "<h1>" + template + "</h1><p>" + params.toString() + "</p>";
    }
}