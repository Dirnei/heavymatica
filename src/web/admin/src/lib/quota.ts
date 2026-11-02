export type QuotaUnit = 'MiB' | 'GiB';

export const unitBytes: Record<QuotaUnit, number> = {
  MiB: 1024 * 1024,
  GiB: 1024 * 1024 * 1024,
};

export const maxQuotaBytes = 100 * unitBytes.GiB;

export const reasonMinLength = 3;
export const reasonMaxLength = 200;

export type QuotaInput = { ok: true; bytes: number } | { ok: false; message: string };

/** Reads a quota typed in MiB or GiB, accepting a comma as the decimal separator, rounded to whole bytes. */
export function parseQuota(input: string, unit: QuotaUnit): QuotaInput {
  const text = input.trim().replace(',', '.');
  if (text === '') {
    return { ok: false, message: 'Enter a quota.' };
  }
  if (!/^\d+(\.\d+)?$|^\.\d+$/.test(text)) {
    return { ok: false, message: 'Enter the quota as a number, for example 500 or 1.5.' };
  }
  const bytes = Math.round(Number(text) * unitBytes[unit]);
  if (bytes > maxQuotaBytes) {
    return { ok: false, message: 'The quota can be at most 100 GiB.' };
  }
  return { ok: true, bytes };
}

/** The quota in the unit an admin would type it in: GiB from 1 GiB up, MiB below, with at most three decimals. */
export function quotaInput(bytes: number): { value: string; unit: QuotaUnit } {
  const unit: QuotaUnit = bytes >= unitBytes.GiB ? 'GiB' : 'MiB';
  const value = Math.round((Math.max(0, bytes) / unitBytes[unit]) * 1000) / 1000;
  return { value: String(value), unit };
}

/** A problem with the reason, or undefined when it can be sent. */
export function reasonProblem(reason: string): string | undefined {
  const length = reason.trim().length;
  if (length < reasonMinLength) {
    return `Give a reason of at least ${reasonMinLength} characters.`;
  }
  if (length > reasonMaxLength) {
    return `The reason can be at most ${reasonMaxLength} characters.`;
  }
  return undefined;
}
