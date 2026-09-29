package org.viajeseventos.security;

/**
 * Who is making a module-gated request, resolved by {@link ModuleAccess} before the handler runs.
 * {@code companyId} scopes everything the handler reads or writes: an account only ever sees its
 * own company's events, bookings and users.
 */
public record Caller(long userId, long companyId) {
}
