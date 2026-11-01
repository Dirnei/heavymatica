import { api } from './api';

export interface ThumbnailPoll {
  intervalMs?: number;
  timeoutMs?: number;
  signal?: AbortSignal;
}

/**
 * Asks `check` every `intervalMs` until it reports a thumbnail, for at most `timeoutMs`; resolves whether one came.
 * The cloud renders a thumbnail for a website upload within seconds, so pages watch a fresh upload this way.
 */
export async function waitForThumbnail(check: () => Promise<boolean>, { intervalMs = 3000, timeoutMs = 60000, signal }: ThumbnailPoll = {}): Promise<boolean> {
  const deadline = Date.now() + timeoutMs;
  while (!signal?.aborted && Date.now() + intervalMs <= deadline) {
    await sleep(intervalMs, signal);
    if (signal?.aborted) {
      return false;
    }
    try {
      if (await check()) {
        return true;
      }
    } catch {
      // A failed request is just another miss; the next one may succeed.
    }
  }
  return false;
}

/** Whether the schematic's version now reports a thumbnail. */
export async function versionHasThumbnail(schematicId: string, number: number): Promise<boolean> {
  const { data } = await api.GET('/v1/schematics/{schematicId}', { params: { path: { schematicId } } });
  return data?.versions.find((version) => version.number === number)?.hasThumbnail ?? false;
}

/** True for a version uploaded so recently that its rendered thumbnail may still be on its way. */
export function mayStillGetThumbnail(version: { hasThumbnail: boolean; createdAt: string }, now = Date.now(), withinMs = 60000): boolean {
  return !version.hasThumbnail && now - Date.parse(version.createdAt) < withinMs;
}

function sleep(ms: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve) => {
    const timer = setTimeout(resolve, ms);
    signal?.addEventListener(
      'abort',
      () => {
        clearTimeout(timer);
        resolve();
      },
      { once: true },
    );
  });
}
