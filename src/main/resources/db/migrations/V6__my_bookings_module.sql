-- Booking trips and "Mis reservas" move out of EVENTS into their own module, MY_BOOKINGS, so a
-- profile can see events without booking. Every profile that could book keeps doing so, except
-- ADMIN: administrators don't book trips for now (AppModule.adminModules() leaves it out).
INSERT INTO profile_modules (profile_id, module_key)
SELECT pm.profile_id, 'MY_BOOKINGS'
FROM profile_modules pm
JOIN profiles p ON p.id = pm.profile_id
WHERE pm.module_key = 'EVENTS' AND (p.code IS NULL OR p.code <> 'ADMIN');
