<script lang="ts">
  import { onMount } from 'svelte';
  import { signInApi } from '../lib/api';
  import { registerWithCode, signInWithPasskey, type Outcome } from '../lib/ceremonies';
  import { afterSignIn, isWellFormedSetupCode, normalizeSetupCode } from '../lib/navigation';
  import { passkeysSupported } from '../lib/webauthn';
  import Notice from './Notice.svelte';

  const query = new URLSearchParams(window.location.search);
  const returnTo = query.get('returnTo');
  const ended = query.get('ended') === '1';
  const supported = passkeysSupported();

  let checking = $state(true);
  let busy = $state(false);
  let signInMessage = $state<{ text: string; cancelled: boolean }>();

  let setupCode = $state('');
  let displayName = $state('');
  let passkeyLabel = $state('');
  let setupMessage = $state<{ text: string; cancelled: boolean }>();

  onMount(async () => {
    try {
      const { data } = await signInApi.GET('/v1/session');
      if (data) {
        window.location.replace(afterSignIn(data, returnTo));
        return;
      }
    } catch {
      // An unreachable service still shows the sign-in; the button reports the failure.
    }
    checking = false;
  });

  function follow(outcome: Outcome): { text: string; cancelled: boolean } | undefined {
    if (outcome.ok) {
      window.location.assign(afterSignIn(outcome.session, returnTo));
      return undefined;
    }
    return { text: outcome.message, cancelled: outcome.cancelled };
  }

  async function signIn() {
    busy = true;
    signInMessage = undefined;
    signInMessage = follow(await signInWithPasskey());
    busy = false;
  }

  async function register(event: SubmitEvent) {
    event.preventDefault();
    setupMessage = undefined;
    if (!isWellFormedSetupCode(setupCode)) {
      setupMessage = { text: 'A setup code has 12 letters and digits. Check it for typos.', cancelled: false };
      return;
    }
    busy = true;
    const name = displayName.trim();
    setupMessage = follow(
      await registerWithCode({ setupCode: normalizeSetupCode(setupCode), ...(name ? { displayName: name } : {}) }, passkeyLabel),
    );
    busy = false;
  }
</script>

<section class="sign-in">
  <h1>Admin sign-in</h1>

  {#if ended}
    <Notice tone="warning" message="Your admin session ended. Sign in again to continue." />
  {/if}
  {#if !supported}
    <Notice
      tone="warning"
      message="This browser does not support passkeys. Use a current browser on a device with a passkey or security key."
    />
  {/if}

  <div class="card stack" aria-busy={checking}>
    <h2>Sign in with a passkey</h2>
    <p>Admins sign in only with a passkey registered for this site. There is no password.</p>
    <Notice tone={signInMessage?.cancelled ? 'info' : 'error'} message={signInMessage?.text} />
    <div class="actions">
      <button type="button" class="primary" onclick={signIn} disabled={busy || checking || !supported}>
        Sign in with a passkey
      </button>
    </div>
  </div>

  <form class="card form" onsubmit={register}>
    <h2>Register with a setup code</h2>
    <p class="hint">
      The setup command on the server prints a code that works once within 15 minutes. It creates a new admin, or adds a
      passkey to an existing admin when the code was made for one.
    </p>
    <label class="field">
      <span>Setup code</span>
      <input
        type="text"
        class="code"
        bind:value={setupCode}
        autocomplete="off"
        autocapitalize="characters"
        spellcheck="false"
        maxlength="32"
        required
      />
    </label>
    <label class="field">
      <span>Display name</span>
      <input type="text" bind:value={displayName} maxlength="60" autocomplete="name" />
      <span class="hint">Needed for a new admin. Leave it empty when the code adds a passkey to your existing admin.</span>
    </label>
    <label class="field">
      <span>Passkey name (optional)</span>
      <input type="text" bind:value={passkeyLabel} maxlength="60" placeholder="For example: Laptop" autocomplete="off" />
    </label>
    <Notice tone={setupMessage?.cancelled ? 'info' : 'error'} message={setupMessage?.text} />
    <div class="actions">
      <button type="submit" class="primary" disabled={busy || checking || !supported || setupCode.trim() === ''}>
        Register a passkey
      </button>
    </div>
  </form>
</section>

<style>
  .sign-in {
    max-width: 36rem;
    margin: 2rem auto 0;
    display: grid;
    gap: 1.2rem;
  }

  .code {
    font-family: var(--mono);
    font-size: 1.2rem;
    letter-spacing: 0.08em;
    text-transform: uppercase;
  }
</style>
