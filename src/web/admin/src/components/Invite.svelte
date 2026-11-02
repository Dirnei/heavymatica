<script lang="ts">
  import { registerWithCode } from '../lib/ceremonies';
  import { afterSignIn, invitationToken } from '../lib/navigation';
  import { passkeysSupported } from '../lib/webauthn';
  import Notice from './Notice.svelte';

  const token = invitationToken(window.location.hash);
  const supported = passkeysSupported();

  let displayName = $state('');
  let passkeyLabel = $state('');
  let busy = $state(false);
  let message = $state<{ text: string; cancelled: boolean }>();

  async function accept(event: SubmitEvent) {
    event.preventDefault();
    if (!token) {
      return;
    }
    busy = true;
    message = undefined;
    const name = displayName.trim();
    const outcome = await registerWithCode({ invitationToken: token, ...(name ? { displayName: name } : {}) }, passkeyLabel);
    if (outcome.ok) {
      window.location.assign(afterSignIn(outcome.session, null));
      return;
    }
    message = { text: outcome.message, cancelled: outcome.cancelled };
    busy = false;
  }
</script>

<section class="invite">
  <h1>Admin invitation</h1>

  {#if !token}
    <Notice
      tone="warning"
      message="This invitation link is incomplete. Open the whole link you were sent, including the part after the #."
    />
  {:else}
    {#if !supported}
      <Notice
        tone="warning"
        message="This browser does not support passkeys. Use a current browser on a device with a passkey or security key."
      />
    {/if}
    <form class="card form" onsubmit={accept}>
      <p>
        Register a passkey to accept the invitation. An invitation for a new admin needs your display name. A recovery
        invitation adds the passkey to your existing admin instead.
      </p>
      <label class="field">
        <span>Display name</span>
        <input type="text" bind:value={displayName} maxlength="60" autocomplete="name" />
        <span class="hint">Leave it empty for a recovery invitation.</span>
      </label>
      <label class="field">
        <span>Passkey name (optional)</span>
        <input type="text" bind:value={passkeyLabel} maxlength="60" placeholder="For example: Phone" autocomplete="off" />
      </label>
      <Notice tone={message?.cancelled ? 'info' : 'error'} message={message?.text} />
      <div class="actions">
        <button type="submit" class="primary" disabled={busy || !supported}>Register a passkey</button>
      </div>
      <p class="hint">The invitation works once and for 24 hours. Nothing is used up until the passkey is registered.</p>
    </form>
  {/if}
</section>

<style>
  .invite {
    max-width: 36rem;
    margin: 2rem auto 0;
    display: grid;
    gap: 1.2rem;
  }
</style>
