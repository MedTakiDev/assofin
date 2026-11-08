package com.association.assofin.mail;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.context.IContext;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Plain unit test: no SMTP server, the sent message is captured. */
class MailServiceTest {

    @Test
    void everyEmailIsSentFromTheConfiguredAddress() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        SpringTemplateEngine templateEngine = mock(SpringTemplateEngine.class);
        when(templateEngine.process(anyString(), any(IContext.class))).thenReturn("<p>body</p>");

        new MailService(mailSender, templateEngine, new MailProperties("no-reply@assofin.org"))
                .sendAccountActivationEmail("member@example.org", "Member", null, "https://app.example.org/x", "Subject");

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        assertThat(sent.getValue().getFrom()).containsExactly(new InternetAddress("no-reply@assofin.org"));
    }
}
