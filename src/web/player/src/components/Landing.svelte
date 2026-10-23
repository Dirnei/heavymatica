<script lang="ts">
  import { onMount } from 'svelte';
  import { api } from '../lib/api';
  import { safeReturnTo, signInUrl } from '../lib/navigation';
  import { signInErrorMessage } from '../lib/problems';

  const query = new URLSearchParams(window.location.search);
  const error = query.get('error');
  const returnTo = safeReturnTo(query.get('returnTo'));
  const message = signInErrorMessage(error);
  const noAccount = error === 'no_account';

  let checking = $state(true);

  onMount(async () => {
    try {
      const { data } = await api.GET('/v1/me');
      if (data) {
        window.location.replace(returnTo);
        return;
      }
    } catch {
      // Unreachable cloud: show the sign-in anyway.
    }
    checking = false;
  });
</script>

<section class="landing">
  <h1>Your schematics, in the browser</h1>
  <p class="lead">
    Upload Litematica, Sponge and structure files, keep every version, and see what they are made of.
  </p>

  {#if message}
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

  .small {
    font-size: 0.85rem;
    margin: 0;
  }
</style>
