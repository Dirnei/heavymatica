<script lang="ts">
  import { onMount } from 'svelte';
  import { formatTime } from '../lib/format';
  import { loadSession, session, signOut } from '../lib/session';

  let { current = '' }: { current?: string } = $props();

  const links = [
    { href: '/accounts/', label: 'Accounts' },
    { href: '/admins/', label: 'Admins' },
    { href: '/audit/', label: 'Audit log' },
    { href: '/passkeys/', label: 'Passkeys' },
  ];

  let signingOut = $state(false);

  onMount(() => {
    loadSession().catch(() => undefined);
  });

  async function leave() {
    signingOut = true;
    await signOut();
  }
</script>

<div class="bar">
  {#if $session}
    <nav class="site-nav" aria-label="Admin pages">
      {#each links as link (link.href)}
        <a href={link.href} aria-current={current === link.href ? 'page' : undefined}>{link.label}</a>
      {/each}
    </nav>
  {/if}
  {#if $session}
    <span class="name" title={`Session ends at the latest ${formatTime($session.expiresAt)}`}>{$session.displayName}</span>
  {/if}
  <button type="button" onclick={leave} disabled={signingOut}>Sign out</button>
</div>

<style>
  .bar {
    display: flex;
    align-items: center;
    gap: 1rem;
    flex-wrap: wrap;
  }

  .name {
    font-weight: 600;
  }
</style>
