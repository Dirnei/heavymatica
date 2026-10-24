<script lang="ts">
  import { onMount, untrack } from 'svelte';

  interface Props {
    name: string;
    /** Renames to the trimmed name and returns an error message when it failed. */
    onrename: (name: string) => Promise<string | undefined>;
    onclose: () => void;
  }

  const { name, onrename, onclose }: Props = $props();

  let dialog: HTMLDialogElement;
  let input: HTMLInputElement;
  let value = $state(untrack(() => name));
  let busy = $state(false);
  let error = $state<string>();

  onMount(() => {
    dialog.showModal();
    input.select();
  });

  async function submit(event: SubmitEvent) {
    event.preventDefault();
    const next = value.trim();
    if (!next || next === name) {
      dialog.close();
      return;
    }
    busy = true;
    error = undefined;
    try {
      error = await onrename(next);
      if (!error) {
        dialog.close();
      }
    } finally {
      busy = false;
    }
  }
</script>

<dialog bind:this={dialog} onclose={onclose} aria-labelledby="rename-schematic-title">
  <form onsubmit={submit}>
    <h2 id="rename-schematic-title">Rename schematic</h2>
    <label class="field">
      <span>Name</span>
      <input type="text" bind:this={input} bind:value maxlength="100" required />
    </label>
    {#if error}
      <p class="notice error" role="alert">{error}</p>
    {/if}
    <div class="dialog-actions">
      <button type="button" onclick={() => dialog.close()}>Cancel</button>
      <button type="submit" class="primary" disabled={busy}>Rename</button>
    </div>
  </form>
</dialog>

<style>
  .field {
    display: grid;
    gap: 0.3rem;
  }
</style>
