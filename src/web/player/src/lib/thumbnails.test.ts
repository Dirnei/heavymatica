import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mayStillGetThumbnail, waitForThumbnail } from './thumbnails';

describe('waitForThumbnail', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('stops as soon as the thumbnail is there', async () => {
    const check = vi.fn().mockResolvedValueOnce(false).mockResolvedValueOnce(true);
    const waiting = waitForThumbnail(check);

    await vi.advanceTimersByTimeAsync(6000);

    await expect(waiting).resolves.toBe(true);
    expect(check).toHaveBeenCalledTimes(2);
    await vi.advanceTimersByTimeAsync(30000);
    expect(check).toHaveBeenCalledTimes(2);
  });

  it('gives up after a minute', async () => {
    const check = vi.fn().mockResolvedValue(false);
    const waiting = waitForThumbnail(check);

    await vi.advanceTimersByTimeAsync(61000);

    await expect(waiting).resolves.toBe(false);
    expect(check).toHaveBeenCalledTimes(20);
  });

  it('stops when the page is left', async () => {
    const check = vi.fn().mockResolvedValue(false);
    const controller = new AbortController();
    const waiting = waitForThumbnail(check, { signal: controller.signal });

    await vi.advanceTimersByTimeAsync(3000);
    controller.abort();
    await vi.advanceTimersByTimeAsync(30000);

    await expect(waiting).resolves.toBe(false);
    expect(check).toHaveBeenCalledTimes(1);
  });

  it('keeps asking after a failed request', async () => {
    const check = vi.fn().mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(true);
    const waiting = waitForThumbnail(check);

    await vi.advanceTimersByTimeAsync(6000);

    await expect(waiting).resolves.toBe(true);
  });
});

describe('mayStillGetThumbnail', () => {
  const now = Date.parse('2026-11-01T12:00:00Z');

  it('watches a fresh upload without a thumbnail', () => {
    expect(mayStillGetThumbnail({ hasThumbnail: false, createdAt: '2026-11-01T11:59:30Z' }, now)).toBe(true);
  });

  it('leaves older versions and those with a thumbnail alone', () => {
    expect(mayStillGetThumbnail({ hasThumbnail: false, createdAt: '2026-11-01T11:58:00Z' }, now)).toBe(false);
    expect(mayStillGetThumbnail({ hasThumbnail: true, createdAt: '2026-11-01T11:59:30Z' }, now)).toBe(false);
  });
});
