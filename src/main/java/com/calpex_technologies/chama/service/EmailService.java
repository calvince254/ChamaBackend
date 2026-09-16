package com.calpex_technologies.chama.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.security.SecureRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@chama.com}")
    private String fromEmail;

    @Value("${app.name:Chama Manager}")
    private String appName;

    private static final String CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    public static final String DEFAULT_PASSWORD = "12345678";

    /**
     * Generate a random password of specified length
     */
    public String generateRandomPassword(int length) {
        StringBuilder password = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            password.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
        }
        return password.toString();
    }

    /**
     * Generate a random password with default length of 10
     */
    public String generateRandomPassword() {
        return generateRandomPassword(10);
    }

    /**
     * Send welcome email with credentials to new user
     */
    @Async
    public void sendWelcomeEmail(String toEmail, String name, String password, String chamaName) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Welcome to " + chamaName + " - Your Login Credentials");

            String htmlContent = buildWelcomeEmailHtml(name, toEmail, password, chamaName);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Welcome email sent successfully to: {}", toEmail);
        } catch (MessagingException e) {
            log.error("Failed to send welcome email to {}: {}", toEmail, e.getMessage());
        }
    }

    /**
     * Send password reset email
     */
    @Async
    public void sendPasswordResetEmail(String toEmail, String name, String newPassword) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(appName + " - Password Reset");

            String htmlContent = buildPasswordResetEmailHtml(name, newPassword);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Password reset email sent successfully to: {}", toEmail);
        } catch (MessagingException e) {
            log.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
        }
    }

    /**
     * Send simple text email
     */
    @Async
    public void sendSimpleEmail(String toEmail, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(text);

            mailSender.send(message);
            log.info("Email sent successfully to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", toEmail, e.getMessage());
        }
    }

    private String buildWelcomeEmailHtml(String name, String email, String password, String chamaName) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: 'Segoe UI', Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: #0b4f47; color: white; padding: 20px; text-align: center; border-radius: 8px 8px 0 0; }
                    .content { background: #f9fafb; padding: 30px; border: 1px solid #e5e7eb; }
                    .credentials { background: white; padding: 20px; border-radius: 8px; margin: 20px 0; border: 1px solid #e5e7eb; }
                    .credential-item { margin: 10px 0; }
                    .credential-label { font-size: 12px; color: #6b7280; text-transform: uppercase; }
                    .credential-value { font-size: 16px; font-weight: bold; color: #0b4f47; font-family: monospace; }
                    .warning { background: #fef3c7; border: 1px solid #f59e0b; padding: 15px; border-radius: 8px; margin: 20px 0; }
                    .footer { text-align: center; padding: 20px; color: #6b7280; font-size: 12px; }
                    .btn { display: inline-block; background: #f5a623; color: #26170a; padding: 12px 24px; text-decoration: none; border-radius: 6px; font-weight: bold; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Welcome to %s</h1>
                    </div>
                    <div class="content">
                        <p>Hello <strong>%s</strong>,</p>
                        <p>Your account has been created for <strong>%s</strong>. Below are your login credentials:</p>
                        
                        <div class="credentials">
                            <div class="credential-item">
                                <div class="credential-label">Email</div>
                                <div class="credential-value">%s</div>
                            </div>
                            <div class="credential-item">
                                <div class="credential-label">Password</div>
                                <div class="credential-value">%s</div>
                            </div>
                        </div>
                        
                        <div class="warning">
                            <strong>⚠️ Important:</strong> For security reasons, you will be required to change your password when you first log in.
                        </div>
                        
                        <p>If you have any questions, please contact your group administrator.</p>
                    </div>
                    <div class="footer">
                        <p>This is an automated message from %s. Please do not reply to this email.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(chamaName, name, chamaName, email, password, appName);
    }

    private String buildPasswordResetEmailHtml(String name, String newPassword) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: 'Segoe UI', Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: #0b4f47; color: white; padding: 20px; text-align: center; border-radius: 8px 8px 0 0; }
                    .content { background: #f9fafb; padding: 30px; border: 1px solid #e5e7eb; }
                    .credentials { background: white; padding: 20px; border-radius: 8px; margin: 20px 0; border: 1px solid #e5e7eb; }
                    .credential-value { font-size: 18px; font-weight: bold; color: #0b4f47; font-family: monospace; text-align: center; }
                    .warning { background: #fef3c7; border: 1px solid #f59e0b; padding: 15px; border-radius: 8px; margin: 20px 0; }
                    .footer { text-align: center; padding: 20px; color: #6b7280; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Password Reset</h1>
                    </div>
                    <div class="content">
                        <p>Hello <strong>%s</strong>,</p>
                        <p>Your password has been reset. Your new password is:</p>
                        
                        <div class="credentials">
                            <div class="credential-value">%s</div>
                        </div>
                        
                        <div class="warning">
                            <strong>⚠️ Important:</strong> For security reasons, you will be required to change this password when you next log in.
                        </div>
                        
                        <p>If you did not request this password reset, please contact your administrator immediately.</p>
                    </div>
                    <div class="footer">
                        <p>This is an automated message from %s. Please do not reply to this email.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(name, newPassword, appName);
    }
}
