const units = ['KiB', 'MiB', 'GiB', 'TiB'];

export function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes < 1024) {
    return `${Math.max(0, Math.round(bytes || 0))} B`;
  }
  let value = bytes / 1024;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit++;
  }
  return `${value.toFixed(1)} ${units[unit]}`;
}

export function formatDimensions(dimensions: { width: number; height: number; length: number }): string {
  return `${dimensions.width} x ${dimensions.height} x ${dimensions.length}`;
}

export function formatTime(iso: string): string {
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? iso : date.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}

export function formatCount(count: number): string {
  return count.toLocaleString('en-US');
}

/** Share of the quota in use, clamped to 0..100. */
export function usagePercent(usedBytes: number, quotaBytes: number): number {
  if (quotaBytes <= 0) {
    return usedBytes > 0 ? 100 : 0;
  }
  return Math.min(100, Math.max(0, (usedBytes * 100) / quotaBytes));
}
