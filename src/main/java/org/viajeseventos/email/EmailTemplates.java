package org.viajeseventos.email;

import java.util.List;

/** Minimal inline-styled HTML — no templating engine, just string formatting. */
public final class EmailTemplates {

    private EmailTemplates() {
    }

    /** {@code brand} is the company the account belongs to — the emails go out in its name. */
    public static String verifyEmail(String brand, String username, String verifyUrl) {
        return body(brand, "Hola " + escape(username) + ",",
                "Gracias por registrarte en " + escape(brand) + ". Confirma tu correo electrónico para activar tu cuenta:",
                verifyUrl, "Verificar mi correo",
                "Este enlace expira en 24 horas. Si no creaste esta cuenta, puedes ignorar este mensaje.");
    }

    public static String resetPassword(String brand, String username, String resetUrl) {
        return body(brand, "Hola " + escape(username) + ",",
                "Recibimos una solicitud para restablecer tu contraseña en " + escape(brand) + ":",
                resetUrl, "Restablecer contraseña",
                "Este enlace expira en 1 hora. Si no solicitaste esto, puedes ignorar este mensaje — tu contraseña no cambiará.");
    }

    public static String testEmail(String provider) {
        String via = provider != null && !provider.isBlank() ? " vía " + escape(provider) : "";
        return """
                <div style="font-family: Arial, sans-serif; max-width: 480px; margin: 0 auto; color: #1f2937;">
                  <h2 style="color: #2563eb;">Busconciertos</h2>
                  <p>Este es un correo de prueba%s, enviado desde Configuración → Correo.</p>
                  <p>Si lo estás leyendo, el envío de correos está bien configurado.</p>
                </div>
                """.formatted(via);
    }

    /** Your seats are booked, with where and when you board. */
    public static String bookingConfirmed(String brand, String name, String tripTitle, List<String> details, String url) {
        return notice(brand, "Hola " + escape(name) + ",",
                "Tu reserva para <strong>" + escape(tripTitle) + "</strong> quedó registrada.",
                details, url, "Ver mi reserva",
                "Llega al punto de encuentro 10 minutos antes de la hora indicada.");
    }

    /** A seat opened up on a trip the person was waiting for — the whole point of the waitlist. */
    public static String seatAvailable(String brand, String name, String tripTitle, List<String> details, String url) {
        return notice(brand, "Hola " + escape(name) + ",",
                "Se liberó un cupo en <strong>" + escape(tripTitle) + "</strong>, la salida que estabas esperando.",
                details, url, "Reservar mi cupo",
                "Los cupos se asignan por orden de llegada: si alguien reserva antes, volverás a la lista de espera.");
    }

    /** The operator called the trip off. */
    public static String tripCancelled(String brand, String name, String tripTitle, String reason, String url) {
        return notice(brand, "Hola " + escape(name) + ",",
                "La salida <strong>" + escape(tripTitle) + "</strong> fue cancelada.",
                reason == null || reason.isBlank() ? List.of() : List.of("Motivo: " + reason),
                url, "Ver otras salidas",
                "Si tienes dudas, responde este correo y te contactamos.");
    }

    /** Sent the day before, with the stop and time the passenger actually has to be at. */
    public static String tripReminder(String brand, String name, String tripTitle, List<String> details, String url) {
        return notice(brand, "Hola " + escape(name) + ",",
                "Mañana es tu viaje a <strong>" + escape(tripTitle) + "</strong>.",
                details, url, "Ver mi ticket",
                "Lleva tu ticket a mano: el conductor lo necesita para marcar tu subida.");
    }

    /** Like {@link #body} but with a list of facts (stop, time, price, seats) above the button. */
    private static String notice(String brand, String greeting, String intro, List<String> details,
                                 String url, String buttonText, String footnote) {
        StringBuilder list = new StringBuilder();
        if (!details.isEmpty()) {
            list.append("<ul style=\"padding-left: 18px; line-height: 1.7;\">");
            for (String detail : details) {
                list.append("<li>").append(escape(detail)).append("</li>");
            }
            list.append("</ul>");
        }
        return """
                <div style="font-family: Arial, sans-serif; max-width: 480px; margin: 0 auto; color: #292524;">
                  <h2 style="color: #2563eb;">%s</h2>
                  <p>%s</p>
                  <p>%s</p>
                  %s
                  <p style="margin: 24px 0;">
                    <a href="%s" style="background: #2563eb; color: #fff; padding: 12px 24px; text-decoration: none; border-radius: 8px; display: inline-block;">%s</a>
                  </p>
                  <p style="color: #78716c; font-size: 13px;">%s</p>
                </div>
                """.formatted(escape(brand), greeting, intro, list, url, buttonText, footnote);
    }

    private static String body(String brand, String greeting, String intro, String url, String buttonText, String footnote) {
        return """
                <div style="font-family: Arial, sans-serif; max-width: 480px; margin: 0 auto; color: #292524;">
                  <h2 style="color: #2563eb;">%s</h2>
                  <p>%s</p>
                  <p>%s</p>
                  <p style="margin: 24px 0;">
                    <a href="%s" style="background: #2563eb; color: #fff; padding: 12px 24px; text-decoration: none; border-radius: 8px; display: inline-block;">%s</a>
                  </p>
                  <p style="color: #78716c; font-size: 13px;">%s</p>
                  <p style="color: #78716c; font-size: 13px;">Si el botón no funciona, copia y pega este enlace: %s</p>
                </div>
                """.formatted(escape(brand), greeting, intro, url, buttonText, footnote, url);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
