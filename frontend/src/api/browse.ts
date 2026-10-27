import { get } from './client'
import type { Category, City, Event, EventFilters, Home, Page, Session, Tier } from './types'

export const browse = {
    cities: () => get<City[]>('/public/cities'),
    categories: () => get<Category[]>('/public/categories'),
    home: (cityId?: string, signal?: AbortSignal) =>
        get<Home>('/public/home/recommend/default', { cityId, limit: 5 }, signal),
    events: (params: EventFilters, signal?: AbortSignal) =>
        get<Page<Event>>('/public/events/page', params, signal),
    event: (id: string, signal?: AbortSignal) =>
        get<Event>(`/public/events/${encodeURIComponent(id)}`, undefined, signal),
    sessions: (eventId: string, signal?: AbortSignal) =>
        get<Session[]>('/public/sessions', { eventId }, signal),
    tiers: (eventId: string, sessionId: string, signal?: AbortSignal) =>
        get<Page<Tier>>(
            '/public/ticket-tiers/page',
            { eventId, sessionId, page: 1, size: 100 },
            signal,
        ),
}
