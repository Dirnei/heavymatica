import { describe, expect, it } from 'vitest';
import { formatBytes, formatDimensions, usagePercent } from './format';

describe('formatBytes', () => {
  it('shows small sizes in bytes', () => {
    expect(formatBytes(0)).toBe('0 B');
    expect(formatBytes(512)).toBe('512 B');
    expect(formatBytes(1023)).toBe('1023 B');
  });

  it('uses binary units with one decimal', () => {
    expect(formatBytes(1024)).toBe('1.0 KiB');
    expect(formatBytes(1536)).toBe('1.5 KiB');
    expect(formatBytes(14 * 1024 * 1024)).toBe('14.0 MiB');
    expect(formatBytes(100 * 1024 * 1024)).toBe('100.0 MiB');
    expect(formatBytes(3 * 1024 * 1024 * 1024)).toBe('3.0 GiB');
  });
});

describe('formatDimensions', () => {
  it('lists width, height and length', () => {
    expect(formatDimensions({ width: 10, height: 5, length: 8 })).toBe('10 x 5 x 8');
  });
});

describe('usagePercent', () => {
  it('is the share of the quota, clamped', () => {
    expect(usagePercent(14, 100)).toBe(14);
    expect(usagePercent(200, 100)).toBe(100);
    expect(usagePercent(0, 0)).toBe(0);
  });
});
