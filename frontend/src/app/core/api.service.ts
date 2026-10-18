import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import {
  CalendarEvent,
  CreateEventRequest,
  CreateTaskRequest,
  Task,
  UpdateEventRequest,
  UpdateTaskRequest,
} from './models';

/** Thin typed wrapper over the REST API. Returns promises because each call is a single response. */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  listTasks(): Promise<Task[]> {
    return firstValueFrom(this.http.get<Task[]>('/api/tasks'));
  }

  createTask(request: CreateTaskRequest): Promise<Task> {
    return firstValueFrom(this.http.post<Task>('/api/tasks', request));
  }

  updateTask(id: number, request: UpdateTaskRequest): Promise<Task> {
    return firstValueFrom(this.http.patch<Task>(`/api/tasks/${id}`, request));
  }

  deleteTask(id: number): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`/api/tasks/${id}`));
  }

  listEvents(): Promise<CalendarEvent[]> {
    return firstValueFrom(this.http.get<CalendarEvent[]>('/api/events'));
  }

  createEvent(request: CreateEventRequest): Promise<CalendarEvent> {
    return firstValueFrom(this.http.post<CalendarEvent>('/api/events', request));
  }

  updateEvent(id: number, request: UpdateEventRequest): Promise<CalendarEvent> {
    return firstValueFrom(this.http.patch<CalendarEvent>(`/api/events/${id}`, request));
  }

  deleteEvent(id: number): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`/api/events/${id}`));
  }
}
