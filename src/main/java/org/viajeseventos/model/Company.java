package org.viajeseventos.model;

import java.time.LocalDateTime;

/**
 * A transport company — the tenant every user and event belongs to. {@code slug} is fixed at
 * creation: it's in the registration link the company hands its clients ({@code /empresa/<slug>}).
 * {@code contactEmail} is the Reply-To of the emails sent on its behalf.
 */
public record Company(long id, String name, String slug, String contactEmail, boolean active,
                      LocalDateTime createdAt) {
}
