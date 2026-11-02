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

export function formatTime(iso: string | null | undefined): string {
  if (!iso) {
    return '–';
  }
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

const providerNames: Record<string, string> = {
  minecraft: 'Minecraft',
  github: 'GitHub',
};

export function providerName(provider: string): string {
  return Object.hasOwn(providerNames, provider) ? providerNames[provider]! : provider;
}

/** A long ID shortened for tables; the full value stays in the title. */
export function shortId(id: string, keep = 10): string {
  return id.length > keep + 1 ? `${id.slice(0, keep)}…` : id;
}
