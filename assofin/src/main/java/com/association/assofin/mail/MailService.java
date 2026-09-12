package com.association.assofin.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.HashMap;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.mail.javamail.MimeMessageHelper.MULTIPART_MODE_MIXED;

@Service
@RequiredArgsConstructor
public class MailService {
    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    private final String FROM = "contacts@avaodessey.com";


    @Async
    public void sendValidationEmail(
            String to,
            String username,
            EmailTemplateName emailTemplateName,
            String confirmationURL,
            String activationCode,
            String subject ) throws MessagingException {
        String templateName;
        if(emailTemplateName == null) {
            templateName = EmailTemplateName.CONFIRM_EMAIL.name();
        } else {
            templateName = emailTemplateName.getName();
        }
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(
                mimeMessage,
                MULTIPART_MODE_MIXED,
                UTF_8.name()
        );
        Map<String,Object> props = new HashMap<>();
        props.put("username", username);
        props.put("confirmation_url", confirmationURL);
        props.put("activation_code", activationCode);
        Context context = new Context();
        context.setVariables(props);
        helper.setFrom(FROM);
        helper.setTo(to);
        helper.setSubject(subject);

        String template = templateEngine.process(templateName, context);
        helper.setText(template, true);
        mailSender.send(mimeMessage);
    }


}
