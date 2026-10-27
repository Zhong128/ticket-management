export type Id = string
export interface City {
    id: Id
    name: string
    province?: string
    hotLevel?: number
}
export interface Category {
    id: Id
    name: string
    sortOrder?: number
}
export interface Event {
    id: Id
    name: string
    artistName: string
    cityId: Id
    cityName: string
    categoryId: Id
    categoryName: string
    venue: string
    coverImage: string
    description: string
    status: number
    minPrice: number | null
    saleStartTime: string
    saleEndTime: string
    eventStartTime: string
    eventEndTime: string
}
export interface Session {
    id: Id
    eventId: Id
    sessionName: string
    sessionTime: string
    status: number
}
export interface Tier {
    id: Id
    tierName: string
    sessionId: Id
    currentPrice: number
    availableStock: number
    status: number
}
export interface Page<T> {
    records: T[]
    total: number
    page: number
    size: number
    totalPages: number
}
export interface Home {
    currentCity: City | null
    categories: { category: Category; events: Event[] }[]
    totalEvents: number
}
export interface EventFilters {
    name?: string
    keyword?: string
    artistName?: string
    cityId?: Id
    categoryId?: Id
    page?: number
    size?: number
}
