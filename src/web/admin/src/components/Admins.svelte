<script lang="ts">
  import { onMount } from 'svelte';
  import { api, type Admin, type Invitation, type InvitationKind, type Passkey } from '../lib/api';
  import { formatTime, shortId } from '../lib/format';
  import { messageOf, offlineMessage } from '../lib/problems';
  import { loadSession } from '../lib/session';
  import Notice from './Notice.svelte';

  let ready = $state(false);
  let admins = $state<Admin[]>([]);
  let invitations = $state<Invitation[]>([]);
  let busy = $state(false);
  let error = $state<string>();
  let done = $state<string>();
  let created = $state<Invitation>();
  let copied = $state(false);

  onMount(async () => {
    try {
      if (!(await loadSession())) {
        return;
      }
      await refresh();
      ready = true;
    } catch {
      error = offlineMessage;
    }
  });

  async function refresh() {
    const [adminList, invitationList] = await Promise.all([api.GET('/v1/admins'), api.GET('/v1/invitations')]);
    if (adminList.data) {
      admins = adminList.data.admins;
    } else {
      error = messageOf(adminList.error);
    }
    if (invitationList.data) {
      invitations = invitationList.data.invitations;
    } else {
      error = messageOf(invitationList.error);
    }
  }

  async function run(action: () => Promise<{ error?: unknown; response: Response }>, success: string): Promise<boolean> {
    busy = true;
    error = undefined;
    done = undefined;
    try {
      const { error: problem, response } = await action();
      if (response.ok) {
        done = success;
        await refresh();
        return true;
      }
      error = messageOf(problem);
    } catch {
      error = offlineMessage;
    } finally {
      busy = false;
    }
    return false;
  }

  async function invite(kind: InvitationKind, admin?: Admin) {
    busy = true;
    error = undefined;
    done = undefined;
    created = undefined;
    copied = false;
    try {
      const { data, error: problem } = await api.POST('/v1/invitations', {
        body: { kind, ...(admin ? { adminId: admin.id } : {}) },
      });
      if (data) {
        created = data;
        await refresh();
      } else {
        error = messageOf(problem);
      }
    } catch {
      error = offlineMessage;
    }
    busy = false;
  }

  async function copy() {
    if (!created?.url) {
      return;
    }
    try {
      await navigator.clipboard.writeText(created.url);
      copied = true;
    } catch {
      copied = false;
      error = 'The link could not be copied. Select it and copy it by hand.';
    }
  }

  function selectAll(event: FocusEvent) {
    (event.currentTarget as HTMLInputElement).select();
  }

  function revoke(invitation: Invitation) {
    if (!window.confirm('Revoke this invitation? Its link stops working at once.')) {
      return;
    }
    if (created?.id === invitation.id) {
      created = undefined;
    }
    void run(
      () => api.DELETE('/v1/invitations/{invitationId}', { params: { path: { invitationId: invitation.id } } }),
      'The invitation was revoked.',
    );
  }

  function removeAdmin(admin: Admin) {
    if (!window.confirm(`Remove the admin ${admin.displayName}? Their sessions end at once and their passkeys stop working.`)) {
      return;
    }
    void run(
      () => api.DELETE('/v1/admins/{adminId}', { params: { path: { adminId: admin.id } } }),
      `${admin.displayName} was removed.`,
    );
  }

  function removePasskey(admin: Admin, passkey: Passkey) {
    const label = passkey.name || 'this passkey';
    if (!window.confirm(`Remove ${label} of ${admin.displayName}? It no longer signs in.`)) {
      return;
    }
    void run(
      () =>
        api.DELETE('/v1/admins/{adminId}/passkeys/{passkeyId}', {
          params: { path: { adminId: admin.id, passkeyId: passkey.id } },
        }),
      'The passkey was removed.',
    );
  }

  function adminRemovalBlocked(admin: Admin): string | undefined {
    if (admin.isYou) {
      return 'You cannot remove yourself.';
    }
    if (admins.length <= 1) {
      return 'The last admin cannot be removed.';
    }
    return undefined;
  }
</script>

<section class="stack">
  <h1>Admins</h1>

  <Notice message={error} />
  <Notice tone="success" message={done} />

  {#if ready}
    {#if created}
      <div class="card stack created" aria-live="polite">
        <h2>{created.kind === 'admin' ? 'Invitation link' : `Recovery link for ${created.adminName ?? 'the admin'}`}</h2>
        {#if created.url}
          <p>
            Send this link to {created.kind === 'admin' ? 'the new admin' : (created.adminName ?? 'the admin')} over a channel
            you trust. <strong>It is shown only now</strong> and works once, until {formatTime(created.expiresAt)}.
          </p>
          <label class="field">
            <span class="visually-hidden">Invitation link</span>
            <input type="text" class="mono" readonly value={created.url} onfocus={selectAll} />
          </label>
          <div class="actions">
            <button type="button" class="primary" onclick={copy}>{copied ? 'Copied' : 'Copy the link'}</button>
            <button type="button" onclick={() => (created = undefined)}>Done</button>
          </div>
        {:else}
          <p class="notice warning">The service created the invitation but sent no link. Revoke it and try again.</p>
        {/if}
      </div>
    {/if}

    <div class="card stack">
      <div class="heading">
        <h2>Open invitations</h2>
        <button type="button" class="primary" onclick={() => invite('admin')} disabled={busy}>Invite an admin</button>
      </div>
      {#if invitations.length === 0}
        <p class="muted">No open invitations.</p>
      {:else}
        <div class="table-wrap">
          <table>
            <thead>
              <tr><th>Kind</th><th>For</th><th>Created by</th><th>Created</th><th>Expires</th><th><span class="visually-hidden">Actions</span></th></tr>
            </thead>
            <tbody>
              {#each invitations as invitation (invitation.id)}
                <tr>
                  <td>{invitation.kind === 'admin' ? 'New admin' : 'Recovery'}</td>
                  <td>{invitation.kind === 'admin' ? '–' : (invitation.adminName ?? invitation.adminId ?? '–')}</td>
                  <td>{invitation.createdBy}</td>
                  <td>{formatTime(invitation.createdAt)}</td>
                  <td>{formatTime(invitation.expiresAt)}</td>
                  <td><button type="button" class="danger" onclick={() => revoke(invitation)} disabled={busy}>Revoke</button></td>
                </tr>
              {/each}
            </tbody>
          </table>
        </div>
      {/if}
    </div>

    <p class="hint">
      Every admin keeps at least one passkey, so an admin's last passkey cannot be removed. An admin who lost their
      passkeys gets a recovery link that adds a new one.
    </p>

    {#each admins as admin (admin.id)}
      {@const blocked = adminRemovalBlocked(admin)}
      <div class="card stack">
        <div class="heading">
          <h2>{admin.displayName}{#if admin.isYou}<span class="tag you">You</span>{/if}</h2>
          <div class="actions">
            {#if !admin.isYou}
              <button type="button" onclick={() => invite('recovery', admin)} disabled={busy}>Create a recovery link</button>
            {/if}
            <button type="button" class="danger" onclick={() => removeAdmin(admin)} disabled={busy || !!blocked} title={blocked}>
              Remove admin
            </button>
          </div>
        </div>
        <p class="muted">Admin since {formatTime(admin.createdAt)}{#if blocked}. {blocked}{/if}</p>
        <div class="table-wrap">
          <table>
            <thead>
              <tr><th>Passkey</th><th>ID</th><th>Added</th><th>Last used</th><th><span class="visually-hidden">Actions</span></th></tr>
            </thead>
            <tbody>
              {#each admin.passkeys as passkey (passkey.id)}
                <tr>
                  <td>{passkey.name || 'Unnamed passkey'}</td>
                  <td class="mono" title={passkey.id}>{shortId(passkey.id)}</td>
                  <td>{formatTime(passkey.createdAt)}</td>
                  <td>{formatTime(passkey.lastUsedAt)}</td>
                  <td>
                    <button
                      type="button"
                      class="danger"
                      onclick={() => removePasskey(admin, passkey)}
                      disabled={busy || admin.passkeys.length <= 1}
                      title={admin.passkeys.length <= 1 ? 'An admin keeps at least one passkey.' : undefined}
                    >
                      Remove
                    </button>
                  </td>
                </tr>
              {:else}
                <tr><td colspan="5" class="muted">No passkeys.</td></tr>
              {/each}
            </tbody>
          </table>
        </div>
      </div>
    {/each}
  {:else if !error}
    <p class="muted" aria-busy="true">Loading…</p>
  {/if}
</section>

<style>
  .heading {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 0.75rem;
    flex-wrap: wrap;
  }

  .you {
    margin-left: 0.5rem;
    vertical-align: middle;
  }

  .created {
    border-color: var(--accent);
  }
</style>
