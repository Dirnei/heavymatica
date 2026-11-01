<script lang="ts">
  import { onDestroy, onMount } from 'svelte';
  import { signInApi, type Account, type WebPending } from '../lib/api';
  import {
    describeLink,
    formatCode,
    isWellFormedCode,
    linkUrl,
    normalizeCode,
    pollIntervalMs,
    providerName,
    statusView,
    type LinkCode,
    type RedeemerKind,
  } from '../lib/links';
  import { libraryPath, signInUrl } from '../lib/navigation';
  import { messageOf, problemMessage } from '../lib/problems';

  type Visitor = { kind: 'account'; account: Account } | { kind: 'pending'; pending: WebPending } | { kind: 'signedOut' };

  let visitor = $state<Visitor>();
  let input = $state(formatCode(new URLSearchParams(window.location.search).get('code')));
  let busy = $state(false);
  let error = $state<string>();
  let link = $state<LinkCode>();
  let timer: ReturnType<typeof setTimeout> | undefined;
  let destroyed = false;

  const redeemer = $derived<RedeemerKind>(visitor?.kind === 'pending' ? 'pending' : 'account');
  const view = $derived(link ? statusView(link.status, redeemer) : undefined);

  onMount(async () => {
    try {
      const { data: account } = await signInApi.GET('/v1/me');
      if (account) {
        visitor = { kind: 'account', account };
        return;
      }
      const { data: pending } = await signInApi.GET('/v1/web/pending');
      visitor = pending ? { kind: 'pending', pending } : { kind: 'signedOut' };
    } catch {
      visitor = { kind: 'signedOut' };
      error = 'The cloud could not be reached. Check your connection and try again.';
    }
  });

  onDestroy(() => {
    destroyed = true;
    clearTimeout(timer);
  });

  async function redeem(event: SubmitEvent) {
    event.preventDefault();
    error = undefined;
    if (!isWellFormedCode(input)) {
      error = problemMessage('link_code_invalid');
      return;
    }
    busy = true;
    try {
      const { data, error: problem, response } = await signInApi.POST('/v1/link-codes/redeem', {
        body: { code: normalizeCode(input) },
      });
      if (response.status === 401) {
        signedOut();
      } else if (data) {
        await show(data);
      } else {
        error = messageOf(problem);
      }
    } catch {
      error = 'The code could not be sent. Check your connection and try again.';
    } finally {
      busy = false;
    }
  }

  async function show(code: LinkCode) {
    link = code;
    if (statusView(code.status, redeemer).waiting) {
      timer = setTimeout(poll, pollIntervalMs);
    } else if (code.status === 'linked' && redeemer === 'pending') {
      await completeSignIn();
    }
  }

  async function poll() {
    if (destroyed || !link) {
      return;
    }
    try {
      const { data, response } = await signInApi.GET('/v1/link-codes/{codeId}', { params: { path: { codeId: link.id } } });
      if (destroyed) {
        return;
      }
      if (data) {
        await show(data);
        return;
      }
      if (response.status === 401) {
        signedOut();
        return;
      }
      if (response.status === 404) {
        link = undefined;
        error = problemMessage('link_code_invalid');
        return;
      }
    } catch {
      // A dropped connection retries on the next tick.
    }
    timer = setTimeout(poll, pollIntervalMs);
  }

  async function completeSignIn() {
    try {
      const { data, error: problem } = await signInApi.POST('/v1/web/pending/session');
      if (data) {
        window.location.assign(libraryPath);
        return;
      }
      error = messageOf(problem);
    } catch {
      error = 'The sign-in could not be completed. Check your connection and reload the page.';
    }
  }

  function signedOut() {
    clearTimeout(timer);
    link = undefined;
    visitor = { kind: 'signedOut' };
    error = 'Your sign-in has ended. Sign in again to use the code.';
  }

  function another() {
    clearTimeout(timer);
    link = undefined;
    error = undefined;
    input = '';
  }
</script>

<section class="link">
  <h1>Link with the game</h1>

  {#if !visitor}
    <p class="muted" aria-busy="true">Checking your sign-in…</p>
  {:else if visitor.kind === 'signedOut'}
    {#if error}
      <p class="notice warning" role="alert">{error}</p>
    {/if}
    <p>Sign in with GitHub to use the code from the game. You come back here with the code filled in.</p>
    <div class="actions">
      <a class="button primary" href={signInUrl('login', linkUrl(input))}>Sign in with GitHub</a>
      <a class="button" href={signInUrl('register', linkUrl(input))}>Register with GitHub</a>
    </div>
  {:else}
    <p class="muted">
      {#if visitor.kind === 'account'}
        Signed in as <strong>{visitor.account.name}</strong> ({providerName(visitor.account.provider)}).
      {:else}
        Signed in with {providerName(visitor.pending.identity.provider)}
        {#if visitor.pending.identity.name}<strong>{visitor.pending.identity.name}</strong>{/if}, without an account yet.
      {/if}
    </p>

    {#if link && view}
      <div class="card status">
        <p>{describeLink(link)}</p>
        <p class="notice {view.tone === 'info' ? '' : view.tone}" role="status">{view.message}</p>
        {#if error}
          <p class="notice error" role="alert">{error}</p>
        {/if}
        {#if view.failed}
          <div class="actions">
            <button type="button" class="primary" onclick={another}>Use another code</button>
          </div>
        {:else if link.status === 'linked' && redeemer === 'account'}
          <div class="actions">
            <a class="button primary" href={libraryPath}>Open the library</a>
          </div>
        {/if}
      </div>
    {:else}
      <p>
        {#if visitor.kind === 'pending'}
          To join the account you use in the game, open the library in the game, choose Account and then Link another
          sign-in. Enter the code it shows here.
        {:else}
          Enter the code the game shows when you sign in there for the first time and choose to join an account.
        {/if}
      </p>
      <form class="card code" onsubmit={redeem}>
        <label>
          <span>Link code</span>
          <input
            type="text"
            bind:value={input}
            placeholder="XXXX-XXXX"
            maxlength="20"
            autocomplete="off"
            autocapitalize="characters"
            spellcheck="false"
            required
          />
        </label>
        {#if error}
          <p class="notice error" role="alert">{error}</p>
        {/if}
        <div class="actions">
          <button type="submit" class="primary" disabled={busy || input.trim() === ''}>Link</button>
        </div>
      </form>
      <p class="muted small">Nothing is linked until you confirm it in the game.</p>
    {/if}
  {/if}
</section>

<style>
  .link {
    max-width: 40rem;
    margin: 3rem auto 0;
    display: grid;
    gap: 1.2rem;
  }

  h1 {
    font-size: 2rem;
    margin: 0;
  }

  p {
    margin: 0;
  }

  .card {
    display: grid;
    gap: 0.8rem;
  }

  label {
    display: grid;
    gap: 0.3rem;
  }

  input {
    font-family: var(--mono);
    font-size: 1.3rem;
    letter-spacing: 0.1em;
    text-transform: uppercase;
  }

  .actions {
    display: flex;
    gap: 0.75rem;
    flex-wrap: wrap;
  }

  .small {
    font-size: 0.85rem;
  }
</style>
