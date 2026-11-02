import { describe, expect, it } from 'vitest';
import { maxQuotaBytes, parseQuota, quotaInput, reasonProblem, unitBytes } from './quota';

const MiB = 1024 * 1024;
const GiB = 1024 * MiB;

describe('parseQuota', () => {
  it('converts MiB and GiB to bytes', () => {
    expect(parseQuota('100', 'MiB')).toEqual({ ok: true, bytes: 100 * MiB });
    expect(parseQuota('1', 'GiB')).toEqual({ ok: true, bytes: GiB });
    expect(parseQuota('1.5', 'GiB')).toEqual({ ok: true, bytes: 1.5 * GiB });
    expect(parseQuota(' 2,5 ', 'MiB')).toEqual({ ok: true, bytes: 2.5 * MiB });
    expect(parseQuota('.5', 'GiB')).toEqual({ ok: true, bytes: 0.5 * GiB });
  });

  it('rounds to whole bytes', () => {
    expect(parseQuota('0.1', 'MiB')).toEqual({ ok: true, bytes: 104858 });
    expect(parseQuota('0.0000001', 'MiB')).toEqual({ ok: true, bytes: 0 });
  });

  it('allows 0 to 100 GiB', () => {
    expect(parseQuota('0', 'MiB')).toEqual({ ok: true, bytes: 0 });
    expect(parseQuota('100', 'GiB')).toEqual({ ok: true, bytes: maxQuotaBytes });
    expect(parseQuota('102400', 'MiB')).toEqual({ ok: true, bytes: maxQuotaBytes });
    expect(maxQuotaBytes).toBe(107374182400);
  });

  it('refuses more than 100 GiB', () => {
    expect(parseQuota('100.001', 'GiB').ok).toBe(false);
    expect(parseQuota('102401', 'MiB').ok).toBe(false);
  });

  it('refuses what is not a non-negative number', () => {
    for (const input of ['', '  ', '-1', 'abc', '1e3', '1.2.3', '0x10', 'Infinity', '1 GiB']) {
      expect(parseQuota(input, 'MiB').ok, input).toBe(false);
    }
  });
});

describe('quotaInput', () => {
  it('shows GiB from 1 GiB up and MiB below', () => {
    expect(quotaInput(GiB)).toEqual({ value: '1', unit: 'GiB' });
    expect(quotaInput(100 * MiB)).toEqual({ value: '100', unit: 'MiB' });
    expect(quotaInput(1536 * MiB)).toEqual({ value: '1.5', unit: 'GiB' });
    expect(quotaInput(0)).toEqual({ value: '0', unit: 'MiB' });
  });

  it('keeps at most three decimals', () => {
    expect(quotaInput(MiB + 1)).toEqual({ value: '1', unit: 'MiB' });
    expect(quotaInput(Math.round(1.23456 * GiB))).toEqual({ value: '1.235', unit: 'GiB' });
  });

  it('round-trips through parseQuota for whole units', () => {
    for (const bytes of [0, 50 * MiB, 512 * MiB, GiB, 10 * GiB, maxQuotaBytes]) {
      const { value, unit } = quotaInput(bytes);
      expect(parseQuota(value, unit)).toEqual({ ok: true, bytes });
    }
    expect(unitBytes.GiB / unitBytes.MiB).toBe(1024);
  });
});

describe('reasonProblem', () => {
  it('needs 3 to 200 characters after trimming', () => {
    expect(reasonProblem('')).toMatch(/at least 3/);
    expect(reasonProblem('  ab  ')).toMatch(/at least 3/);
    expect(reasonProblem('abc')).toBeUndefined();
    expect(reasonProblem('x'.repeat(200))).toBeUndefined();
    expect(reasonProblem('x'.repeat(201))).toMatch(/at most 200/);
  });
});
