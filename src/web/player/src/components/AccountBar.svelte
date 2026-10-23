<script lang="ts">
  import { onMount } from 'svelte';
  import { account, refreshAccount, signOut } from '../lib/session';
  import UsageMeter from './UsageMeter.svelte';

  let signingOut = $state(false);

  onMount(() => {
    void refreshAccount();
  });

  async function leave() {
    signingOut = true;
    await signOut();
  }
</script>

<div class="account">
  {#if $account}
    <UsageMeter usedBytes={$account.usedBytes} quotaBytes={$account.quotaBytes} />
    <span class="name" title={`Signed in with ${$account.provider}`}>{$account.name}</span>
  {/if}
  <button type="button" onclick={leave} disabled={signingOut}>Sign out</button>
</div>

<style>
  .account {
    display: flex;
    align-items: center;
    gap: 1rem;
    flex-wrap: wrap;
  }

  .name {
    font-weight: 600;
  }
</style>
