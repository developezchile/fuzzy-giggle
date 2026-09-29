package org.viajeseventos.service;

import org.viajeseventos.dto.request.EventRequest;
import org.viajeseventos.exception.BusinessRuleException;
import org.viajeseventos.exception.ResourceNotFoundException;
import org.viajeseventos.model.Event;
import org.viajeseventos.repository.EventRepository;
import org.viajeseventos.validation.Slugs;

import java.util.List;

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

    public List<EventRepository.AdminListing> findAll(long companyId) {
        return eventRepository.findAllForAdmin(companyId);
    }

    /** Another company's event is "not found", never "forbidden" — its existence isn't revealed. */
    public Event findById(long companyId, long id) {
        return eventRepository.findById(companyId, id).orElseThrow(() -> new ResourceNotFoundException("Evento no encontrado"));
    }

    public Event create(long companyId, EventRequest req) {
        String slug = uniqueSlug(companyId, req.name);
        long id = eventRepository.insert(companyId, new Event(0, slug, req.name, req.venue, req.commune, req.category,
                req.imageUrl, req.startDate, req.endDate, req.sourceUrl, req.active));
        return findById(companyId, id);
    }

    public Event update(long companyId, long id, EventRequest req) {
        Event existing = findById(companyId, id);
        eventRepository.update(companyId, new Event(id, existing.slug(), req.name, req.venue, req.commune, req.category,
                req.imageUrl, req.startDate, req.endDate, req.sourceUrl, req.active));
        return findById(companyId, id);
    }

    public void delete(long companyId, long id) {
        findById(companyId, id);
        if (eventRepository.hasBookings(id)) {
            throw new BusinessRuleException("El evento tiene reservas. Desactívalo en lugar de eliminarlo para conservarlas.");
        }
        eventRepository.delete(companyId, id);
    }

    /** "Maná - Vivir sin aire Tour" → "mana-vivir-sin-aire-tour", suffixed -2, -3… if the company already has it. */
    private String uniqueSlug(long companyId, String name) {
        return Slugs.unique(Slugs.from(name, 100, "evento"), slug -> eventRepository.existsBySlug(companyId, slug));
    }
}
