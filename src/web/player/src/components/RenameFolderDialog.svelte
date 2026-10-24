<script lang="ts">
  import { onMount, untrack } from 'svelte';
  import type { FolderList } from '../lib/api';
  import { normalizeFolder, renameMerges } from '../lib/folders';

  interface Props {
    folders: FolderList;
    path: string;
    /** Renames to the normalized path and returns an error message when it failed. */
    onrename: (to: string) => Promise<string | undefined>;
    onclose: () => void;
  }

  const { folders, path, onrename, onclose }: Props = $props();

  let dialog: HTMLDialogElement;
  let to = $state(untrack(() => path));
  let busy = $state(false);
  let error = $state<string>();

  const target = $derived(normalizeFolder(to));
  const merges = $derived(renameMerges(folders.folders, path, target));
  const count = $derived(folders.folders.find((entry) => entry.path === path)?.totalCount ?? 0);

  onMount(() => dialog.showModal());

  async function submit(event: SubmitEvent) {
    event.preventDefault();
    if (target === '' || target === path) {
      dialog.close();
      return;
    }
    busy = true;
    error = undefined;
    try {
      error = await onrename(target);
      if (!error) {
        dialog.close();
      }
    } finally {
      busy = false;
    }
  }
</script>

<dialog bind:this={dialog} onclose={onclose} aria-labelledby="rename-title">
  <form onsubmit={submit}>
    <h2 id="rename-title">Rename folder</h2>
    <p class="muted">
      “{path}” holds {count === 1 ? '1 schematic' : `${count} schematics`}, counting its subfolders. All of them move with it.
    </p>
    <label class="path">
      <span>New path</span>
      <input type="text" bind:value={to} maxlength="200" required />
    </label>
    {#if merges}
      <p class="notice warning" role="status">“{target}” already exists. Renaming merges both folders into “{target}”.</p>
    {/if}
    {#if error}
      <p class="notice error" role="alert">{error}</p>
    {/if}
    <div class="dialog-actions">
      <button type="button" onclick={() => dialog.close()}>Cancel</button>
      <button type="submit" class="primary" disabled={busy || target === ''}>{merges ? 'Merge' : 'Rename'}</button>
    </div>
  </form>
</dialog>

<style>
  .path {
    display: grid;
    gap: 0.3rem;
  }
</style>
