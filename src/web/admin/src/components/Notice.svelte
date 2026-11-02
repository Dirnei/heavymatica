<script lang="ts">
  import { focusOnMount } from '../lib/focus';

  type Tone = 'error' | 'warning' | 'success' | 'info';

  let { message, tone = 'error' }: { message: string | undefined; tone?: Tone } = $props();

  const urgent = $derived(tone === 'error' || tone === 'warning');
</script>

{#if message}
  {#key message}
    {#if urgent}
      <p class="notice {tone}" role="alert" tabindex="-1" use:focusOnMount>{message}</p>
    {:else}
      <p class="notice {tone === 'info' ? '' : tone}" role="status">{message}</p>
    {/if}
  {/key}
{/if}
