package org.viajeseventos.email;

/** Minimal inline-styled HTML — no templating engine, just string formatting. */
public final class EmailTemplates {

    private EmailTemplates() {
    }

    public static String verifyEmail(String username, String verifyUrl) {
        return body("Hola " + escape(username) + ",",
                "Gracias por registrarte en Viajes a Eventos. Confirma tu correo electrónico para activar tu cuenta:",
                verifyUrl, "Verificar mi correo",
                "Este enlace expira en 24 horas. Si no creaste esta cuenta, puedes ignorar este mensaje.");
    }

    public static String resetPassword(String username, String resetUrl) {
        return body("Hola " + escape(username) + ",",
                "Recibimos una solicitud para restablecer tu contraseña en Viajes a Eventos:",
                resetUrl, "Restablecer contraseña",
                "Este enlace expira en 1 hora. Si no solicitaste esto, puedes ignorar este mensaje — tu contraseña no cambiará.");
    }

    public static String testEmail(String provider) {
        String via = provider != null && !provider.isBlank() ? " vía " + escape(provider) : "";
        return """
                <div style="font-family: Arial, sans-serif; max-width: 480px; margin: 0 auto; color: #1f2937;">
                  <h2 style="color: #2563eb;">Viajes a Eventos</h2>
                  <p>Este es un correo de prueba%s, enviado desde Configuración → Correo.</p>
                  <p>Si lo estás leyendo, el envío de correos está bien configurado.</p>
                </div>
                """.formatted(via);
    }

    private static String body(String greeting, String intro, String url, String buttonText, String footnote) {
        return """
                <div style="font-family: Arial, sans-serif; max-width: 480px; margin: 0 auto; color: #292524;">
                  <h2 style="color: #2563eb;">Viajes a Eventos</h2>
                  <p>%s</p>
                  <p>%s</p>
                  <p style="margin: 24px 0;">
                    <a href="%s" style="background: #2563eb; color: #fff; padding: 12px 24px; text-decoration: none; border-radius: 8px; display: inline-block;">%s</a>
                  </p>
                  <p style="color: #78716c; font-size: 13px;">%s</p>
                  <p style="color: #78716c; font-size: 13px;">Si el botón no funciona, copia y pega este enlace: %s</p>
                </div>
                """.formatted(greeting, intro, url, buttonText, footnote, url);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
