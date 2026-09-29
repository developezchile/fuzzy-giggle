package org.viajeseventos.service;

import org.viajeseventos.dto.request.EventRequest;
import org.viajeseventos.exception.BusinessRuleException;
import org.viajeseventos.exception.ResourceNotFoundException;
import org.viajeseventos.model.Event;
import org.viajeseventos.repository.EventRepository;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Event administration (EVENT_ADMIN module). Deactivating hides an event from clients and stops
 * new bookings while keeping existing ones; deleting is only for events nobody ever booked, so a
 * booking's event is never lost.
 */
public final class EventService {

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public List<EventRepository.AdminListing> findAll() {
        return eventRepository.findAllForAdmin();
    }

    public Event findById(long id) {
        return eventRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Evento no encontrado"));
    }

    public Event create(EventRequest req) {
        String slug = uniqueSlug(req.name);
        long id = eventRepository.insert(new Event(0, slug, req.name, req.venue, req.commune, req.category,
                req.imageUrl, req.startDate, req.endDate, req.sourceUrl, req.active));
        return findById(id);
    }

    public Event update(long id, EventRequest req) {
        Event existing = findById(id);
        eventRepository.update(new Event(id, existing.slug(), req.name, req.venue, req.commune, req.category,
                req.imageUrl, req.startDate, req.endDate, req.sourceUrl, req.active));
        return findById(id);
    }

    public void delete(long id) {
        findById(id);
        if (eventRepository.hasBookings(id)) {
            throw new BusinessRuleException("El evento tiene reservas. Desactívalo en lugar de eliminarlo para conservarlas.");
        }
        eventRepository.delete(id);
    }

    /** "Maná - Vivir sin aire Tour" → "mana-vivir-sin-aire-tour", suffixed -2, -3… if taken. */
    private String uniqueSlug(String name) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (base.isEmpty()) base = "evento";
        if (base.length() > 100) base = base.substring(0, 100).replaceAll("-+$", "");
        String slug = base;
        for (int n = 2; eventRepository.existsBySlug(slug); n++) {
            slug = base + "-" + n;
        }
        return slug;
    }
}
