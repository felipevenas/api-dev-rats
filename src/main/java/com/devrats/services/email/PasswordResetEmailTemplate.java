package com.devrats.services.email;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class PasswordResetEmailTemplate {

    private static final String SUBJECT = "Redefina sua senha no DevRats";
    private static final String TEST_SUBJECT = "[TESTE] " + SUBJECT;

    public EmailContent build(PasswordResetEmail email, boolean testMode, String intendedRecipient) {
        String safeName = email.displayName() == null || email.displayName().isBlank()
                ? "dev"
                : email.displayName().trim();
        String escapedName = HtmlUtils.htmlEscape(safeName);
        String escapedUrl = HtmlUtils.htmlEscape(email.resetUrl());
        String testBanner = testMode ? testBanner(intendedRecipient) : "";
        String subject = testMode ? TEST_SUBJECT : SUBJECT;

        String html = """
                <!doctype html>
                <html lang="pt-BR">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Redefina sua senha no DevRats</title>
                </head>
                <body style="margin:0;padding:0;background:#f2f4f7;font-family:Arial,Helvetica,sans-serif;color:#0b0d12;">
                  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#f2f4f7;padding:24px 12px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:560px;background:#ffffff;border:1px solid #e6e9f0;border-radius:16px;overflow:hidden;">
                          <tr>
                            <td style="padding:24px 28px 0;">
                              <span style="font-size:20px;font-weight:700;letter-spacing:-0.5px;color:#0b0d12;">DevRats<span style="color:#7aa600;">.</span></span>
                            </td>
                          </tr>
                          {{TEST_BANNER}}
                          <tr>
                            <td style="padding:24px 28px 8px;">
                              <h1 style="margin:0 0 12px;font-size:24px;line-height:1.25;color:#0b0d12;">Redefinir sua senha</h1>
                              <p style="margin:0 0 16px;font-size:15px;line-height:1.6;color:#3f4756;">Olá, {{NAME}}.</p>
                              <p style="margin:0 0 20px;font-size:15px;line-height:1.6;color:#3f4756;">
                                Recebemos um pedido para redefinir a senha da sua conta DevRats. Clique no botão abaixo para criar uma nova senha.
                              </p>
                              <table role="presentation" cellpadding="0" cellspacing="0" style="margin:0 0 20px;">
                                <tr>
                                  <td style="border-radius:10px;background:#c8ff3d;">
                                    <a href="{{URL}}" style="display:inline-block;padding:14px 22px;font-size:15px;font-weight:700;color:#0a0d02;text-decoration:none;">Criar nova senha</a>
                                  </td>
                                </tr>
                              </table>
                              <p style="margin:0 0 8px;font-size:13px;line-height:1.6;color:#5b6472;">Se o botão não funcionar, copie e cole este link no navegador:</p>
                              <p style="margin:0 0 20px;font-size:12px;line-height:1.6;word-break:break-all;color:#3f4756;">{{URL}}</p>
                              <p style="margin:0 0 8px;font-size:13px;line-height:1.6;color:#5b6472;">
                                O link expira em <strong>{{MINUTES}} minutos</strong> e só pode ser usado uma vez.
                              </p>
                              <p style="margin:0;font-size:13px;line-height:1.6;color:#5b6472;">
                                Se você não solicitou, ignore este e-mail. Sua senha continua a mesma.
                              </p>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:20px 28px 28px;">
                              <hr style="border:none;border-top:1px solid #e6e9f0;margin:0 0 16px;">
                              <p style="margin:0;font-size:12px;line-height:1.6;color:#667085;">
                                DevRats · constância que compila.<br>
                                Este é um e-mail transacional; não é necessário responder.
                              </p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """
                .replace("{{TEST_BANNER}}", testBanner)
                .replace("{{NAME}}", escapedName)
                .replace("{{URL}}", escapedUrl)
                .replace("{{MINUTES}}", Long.toString(email.expiresInMinutes()));

        String text = """
                Olá, %s.

                Recebemos um pedido para redefinir a senha da sua conta DevRats.

                Abra o link abaixo para criar uma nova senha:
                %s

                O link expira em %d minutos e só pode ser usado uma vez.

                Se você não solicitou, ignore este e-mail. Sua senha continua a mesma.
                """
                .formatted(safeName, email.resetUrl(), email.expiresInMinutes());

        return new EmailContent(subject, html, text);
    }

    private String testBanner(String intendedRecipient) {
        String recipient = HtmlUtils.htmlEscape(
                intendedRecipient == null || intendedRecipient.isBlank() ? "destinatário não informado" : intendedRecipient);
        return """
                <tr>
                  <td style="padding:16px 28px 0;">
                    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#fff1d8;border:1px solid #f0c26b;border-radius:10px;">
                      <tr>
                        <td style="padding:12px 14px;font-size:12px;line-height:1.5;color:#7a4a00;">
                          <strong>MODO DE TESTE.</strong> Este e-mail seria enviado para {{RECIPIENT}}.
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
                """.replace("{{RECIPIENT}}", recipient);
    }
}
