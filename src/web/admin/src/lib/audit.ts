import { formatBytes } from './format';

const labels: Record<string, string> = {
  admin_signed_in: 'Signed in',
  admin_sign_in_failed: 'Sign-in failed',
  setup_code_failed: 'Setup code refused',
  invitation_failed: 'Invitation refused',
  admin_created: 'Admin created',
  admin_removed: 'Admin removed',
  passkey_added: 'Passkey added',
  passkey_removed: 'Passkey removed',
  invitation_created: 'Invitation created',
  invitation_revoked: 'Invitation revoked',
  quota_changed: 'Quota changed',
};

/** A readable name for an audit kind; kinds this site does not know yet are shown as they are. */
export function auditLabel(kind: string): string {
  return Object.hasOwn(labels, kind) ? labels[kind]! : kind.replace(/_/g, ' ');
}

const byteFields = new Set(['oldBytes', 'newBytes']);

const fieldNames: Record<string, string> = {
  accountId: 'Account',
  oldBytes: 'From',
  newBytes: 'To',
  reason: 'Reason',
};

export interface AuditDetail {
  key: string;
  label: string;
  value: string;
}

export function auditDetails(details: Record<string, string> | undefined): AuditDetail[] {
  return Object.entries(details ?? {}).map(([key, raw]) => {
    const bytes = byteFields.has(key) && /^\d+$/.test(raw) ? Number(raw) : undefined;
    return {
      key,
      label: Object.hasOwn(fieldNames, key) ? fieldNames[key]! : key,
      value: bytes === undefined ? raw : formatBytes(bytes),
    };
  });
}
