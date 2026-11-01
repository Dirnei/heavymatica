<script lang="ts">
  import { onMount } from 'svelte';
  import { api, type WebPending } from '../lib/api';
  import { linkPath, providerName } from '../lib/links';
  import { safeReturnTo, signInUrl } from '../lib/navigation';
  import { messageOf, signInErrorMessage } from '../lib/problems';

  const query = new URLSearchParams(window.location.search);
  const error = query.get('error');
  const returnTo = safeReturnTo(query.get('returnTo'));
  const message = signInErrorMessage(error);
  const noAccount = error === 'no_account';
  const choosing = query.get('choice') === '1';
  // A sign-in started on the link code page joins with the code it carried.
  const joinTo = returnTo.startsWith(linkPath) ? returnTo : linkPath;

  let checking = $state(true);
  let pending = $state<WebPending>();
  let pendingExpired = $state(false);
  let creating = $state(false);
  let choiceError = $state<string>();

  onMount(async () => {
    try {
      const { data } = await api.GET('/v1/me');
      if (data) {
        window.location.replace(returnTo);
        return;
      }
      if (choosing) {
        const { data: found } = await api.GET('/v1/web/pending');
        pending = found;
        pendingExpired = !found;
      }
    } catch {
      // Unreachable cloud: show the sign-in anyway.
    }
    checking = false;
  });

  async function createAccount() {
    creating = true;
    choiceError = undefined;
    try {
      const { data, error: problem } = await api.POST('/v1/web/pending/account');
      if (data) {
        window.location.assign(returnTo);
        return;
      }
      choiceError = messageOf(problem);
    } catch {
      choiceError = 'The account could not be created. Check your connection and try again.';
    }
    creating = false;
  }
</script>

<section class="landing">
  <h1>Your schematics, in the browser</h1>
  <p class="lead">
    Upload Litematica, Sponge and structure files, keep every version, and see what they are made of.
  </p>

  {#if pending}
    <div class="choice card">
      <h2>No account yet</h2>
      <p>
        There is no Heavymatica account for the {providerName(pending.identity.provider)} sign-in
        {#if pending.identity.name}<strong>{pending.identity.name}</strong>{/if}. Create a new account, or join the account
        you already use in the game.
      </p>
      {#if choiceError}
        <p class="notice error" role="alert">{choiceError}</p>
      {/if}
      <div class="actions">
        <button type="button" class="primary" onclick={createAccount} disabled={creating}>Create a new account</button>
        {#if pending.joinAvailable}
          <a class="button" href={joinTo}>Join an account from the game</a>
        {/if}
      </div>
      <p class="muted small">Nothing is created until you choose.</p>
    </div>
  {:else}
    {#if pendingExpired}
      <p class="notice warning" role="alert">The sign-in took too long. Sign in again.</p>
    {:else if message}
      <p class="notice" class:error={!noAccount && error !== 'signed_out'} class:warning={noAccount || error === 'signed_out'} role="alert">
        {message}
      </p>
    {/if}

    <div class="actions" aria-busy={checking}>
      {#if noAccount}
        <a class="button primary" href={signInUrl('register', returnTo)}>Register with GitHub</a>
        <a class="button" href={signInUrl('login', returnTo)}>Sign in with GitHub</a>
      {:else}
        <a class="button primary" href={signInUrl('login', returnTo)}>Sign in with GitHub</a>
        <a class="button" href={signInUrl('register', returnTo)}>Register with GitHub</a>
      {/if}
    </div>
  {/if}

  <p class="notice">
    The website's library is not connected to the game yet. Schematics you upload here stay separate from the library you
    use in Minecraft for now.
  </p>
  <p class="muted small">Heavymatica only stores your GitHub user ID and name, never your email address.</p>
</section>

<style>
  .landing {
    max-width: 40rem;
    margin: 3rem auto 0;
    display: grid;
    gap: 1.2rem;
  }

  h1 {
    font-size: 2rem;
    margin: 0;
  }

  .lead {
    font-size: 1.1rem;
    margin: 0;
  }

  .actions {
    display: flex;
    gap: 0.75rem;
    flex-wrap: wrap;
  }

  .choice {
    display: grid;
    gap: 0.8rem;
  }

  .choice h2,
  .choice p {
    margin: 0;
  }

  .small {
    font-size: 0.85rem;
    margin: 0;
  }
</style>
