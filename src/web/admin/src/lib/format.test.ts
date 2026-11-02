import { describe, expect, it } from 'vitest';
import { auditDetails, auditLabel } from './audit';
import { formatBytes, providerName, shortId, usagePercent } from './format';

describe('formatBytes', () => {
  it('shows small sizes in bytes', () => {
    expect(formatBytes(0)).toBe('0 B');
    expect(formatBytes(1023)).toBe('1023 B');
  });

  it('uses binary units with one decimal', () => {
    expect(formatBytes(1536)).toBe('1.5 KiB');
    expect(formatBytes(100 * 1024 * 1024)).toBe('100.0 MiB');
    expect(formatBytes(100 * 1024 * 1024 * 1024)).toBe('100.0 GiB');
  });
});

describe('usagePercent', () => {
  it('is the share of the quota, clamped', () => {
    expect(usagePercent(14, 100)).toBe(14);
    expect(usagePercent(200, 100)).toBe(100);
    expect(usagePercent(1, 0)).toBe(100);
    expect(usagePercent(0, 0)).toBe(0);
  });
});

describe('providerName and shortId', () => {
  it('names known providers and keeps others', () => {
    expect(providerName('minecraft')).toBe('Minecraft');
    expect(providerName('github')).toBe('GitHub');
    expect(providerName('other')).toBe('other');
  });

  it('shortens long IDs only', () => {
    expect(shortId('0123456789abcdef')).toBe('0123456789…');
    expect(shortId('short')).toBe('short');
  });
});

describe('audit', () => {
  it('labels known kinds and keeps unknown ones readable', () => {
    expect(auditLabel('quota_changed')).toBe('Quota changed');
    expect(auditLabel('something_new')).toBe('something new');
  });

  it('shows quota changes in binary units', () => {
    expect(
      auditDetails({ accountId: 'abc', oldBytes: String(100 * 1024 * 1024), newBytes: String(1024 ** 3), reason: 'creator' }),
    ).toEqual([
      { key: 'accountId', label: 'Account', value: 'abc' },
      { key: 'oldBytes', label: 'From', value: '100.0 MiB' },
      { key: 'newBytes', label: 'To', value: '1.0 GiB' },
      { key: 'reason', label: 'Reason', value: 'creator' },
    ]);
    expect(auditDetails(undefined)).toEqual([]);
  });
});
