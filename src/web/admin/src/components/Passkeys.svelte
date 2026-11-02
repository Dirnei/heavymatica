<script lang="ts">
  import { onMount } from 'svelte';
  import { api, type Passkey } from '../lib/api';
  import { addPasskey } from '../lib/ceremonies';
  import { formatTime, shortId } from '../lib/format';
  import { accountsPath } from '../lib/navigation';
  import { offlineMessage } from '../lib/problems';
  import { loadSession, session, updateSession } from '../lib/session';
  import { passkeysSupported } from '../lib/webauthn';
  import Notice from './Notice.svelte';

  const supported = passkeysSupported();

  let loaded = $state(false);
  let loadError = $state<string>();
  let passkeys = $state<Passkey[]>();
  let passkeyLabel = $state('');
  let busy = $state(false);
  let message = $state<{ text: string; tone: 'error' | 'info' | 'success' }>();

  onMount(async () => {
    try {
      const current = await loadSession();
      if (current && !current.needsSecondPasskey) {
        await loadPasskeys();
      }
      loaded = !!current;
    } catch {
      loadError = offlineMessage;
    }
  });

  async function loadPasskeys() {
    const { data } = await api.GET('/v1/admins');
    passkeys = data?.admins.find((admin) => admin.isYou)?.passkeys;
  }

  async function add(event: SubmitEvent) {
    event.preventDefault();
    busy = true;
    message = undefined;
    const outcome = await addPasskey(passkeyLabel);
    if (outcome.ok) {
      updateSession(outcome.session);
      passkeyLabel = '';
      message = {
        tone: 'success',
        text: outcome.session.needsSecondPasskey
          ? 'The passkey was added.'
          : `The passkey was added. You now have ${outcome.session.passkeyCount} passkeys.`,
      };
      if (!outcome.session.needsSecondPasskey) {
        await loadPasskeys().catch(() => undefined);
      }
    } else {
      message = { tone: outcome.cancelled ? 'info' : 'error', text: outcome.message };
    }
    busy = false;
  }
</script>

<section class="passkeys stack">
  <h1>Passkeys</h1>

  <Notice message={loadError} />

  {#if loaded && $session}
    {#if $session.needsSecondPasskey}
      <div class="notice warning">
        <p>
          <strong>Register a second passkey to continue.</strong> Every admin needs two passkeys, ideally on different
          devices or a security key, so that losing one does not lock you out. Until then this page and signing out are
          all the admin site offers.
        </p>
      </div>
    {:else}
      <p>You have {$session.passkeyCount} passkeys. Passkeys are removed on the <a href="/admins/">Admins</a> page.</p>
    {/if}

    <form class="card form" onsubmit={add}>
      <h2>Add a passkey</h2>
      <p class="hint">
        Use a different device or security key than the passkeys you already have. The browser refuses one that is
        already registered.
      </p>
      <label class="field">
        <span>Passkey name (optional)</span>
        <input type="text" bind:value={passkeyLabel} maxlength="60" placeholder="For example: Security key" autocomplete="off" />
      </label>
      <Notice tone={message?.tone ?? 'error'} message={message?.text} />
      <div class="actions">
        <button type="submit" class="primary" disabled={busy || !supported}>Add a passkey</button>
        {#if !$session.needsSecondPasskey}
          <a class="button" href={accountsPath}>Continue to the accounts</a>
        {/if}
      </div>
    </form>

    {#if passkeys}
      <div class="card stack">
        <h2>Your passkeys</h2>
        <div class="table-wrap">
          <table>
            <thead>
              <tr><th>Name</th><th>ID</th><th>Added</th><th>Last used</th></tr>
            </thead>
            <tbody>
              {#each passkeys as passkey (passkey.id)}
                <tr>
                  <td>{passkey.name || 'Unnamed passkey'}</td>
                  <td class="mono" title={passkey.id}>{shortId(passkey.id)}</td>
                  <td>{formatTime(passkey.createdAt)}</td>
                  <td>{formatTime(passkey.lastUsedAt)}</td>
                </tr>
              {/each}
            </tbody>
          </table>
        </div>
      </div>
    {/if}
  {:else if !loadError}
    <p class="muted" aria-busy="true">Loading…</p>
  {/if}
</section>

<style>
  .passkeys {
    max-width: 48rem;
  }
</style>
