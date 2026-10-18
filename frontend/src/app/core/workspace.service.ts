import { HttpClient, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { Workspace } from './models';

const STORAGE_KEY = 'calendar-copilot.workspace-id';
export const WORKSPACE_HEADER = 'X-Workspace-Id';

/**
 * Each visitor gets an isolated guest workspace. Its id lives in localStorage and is
 * sent on every API call, so visitors never see or change each other's data.
 */
@Injectable({ providedIn: 'root' })
export class WorkspaceService {
  private readonly http = inject(HttpClient);
  private workspaceId: string | null = readStoredId();

  get id(): string | null {
    return this.workspaceId;
  }

  /** Reuses the stored workspace if the server still has it; otherwise creates a new demo workspace. */
  async ensure(): Promise<string> {
    if (this.workspaceId) {
      try {
        await firstValueFrom(this.http.get<Workspace>('/api/workspaces/current'));
        return this.workspaceId;
      } catch (e) {
        if (!(e instanceof HttpErrorResponse && (e.status === 404 || e.status === 400))) {
          throw e;
        }
      }
    }
    return this.reset();
  }

  /** Starts over with a fresh copy of the demo data. */
  async reset(): Promise<string> {
    this.workspaceId = null;
    const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
    const workspace = await firstValueFrom(this.http.post<Workspace>('/api/workspaces', { timeZone }));
    this.workspaceId = workspace.id;
    try {
      localStorage.setItem(STORAGE_KEY, workspace.id);
    } catch {
      // Storage can be unavailable (private mode); the session still works until reload.
    }
    return workspace.id;
  }
}

/** Adds the workspace header to every API request once a workspace exists. */
export const workspaceInterceptor: HttpInterceptorFn = (req, next) => {
  const id = inject(WorkspaceService).id;
  if (id && req.url.startsWith('/api/')) {
    return next(req.clone({ setHeaders: { [WORKSPACE_HEADER]: id } }));
  }
  return next(req);
};

function readStoredId(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}
