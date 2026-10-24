<script lang="ts">
  import { onMount, untrack } from 'svelte';
  import type { FolderList } from '../lib/api';
  import { buildTree, flattenTree, normalizeFolder } from '../lib/folders';

  interface Props {
    folders: FolderList;
    /** What is being moved, for the title: "3 schematics" or a schematic's name. */
    subject: string;
    initial: string;
    /** Moves to the normalized folder ('' for no folder) and returns an error message when it failed. */
    onmove: (folder: string) => Promise<string | undefined>;
    onclose: () => void;
  }

  const { folders, subject, initial, onmove, onclose }: Props = $props();

  const choices = $derived(flattenTree(buildTree(folders.folders)));

  let dialog: HTMLDialogElement;
  let path = $state(untrack(() => initial));
  let busy = $state(false);
  let error = $state<string>();

  const target = $derived(normalizeFolder(path));
  const isNew = $derived(target !== '' && !folders.folders.some((entry) => entry.path === target));

  onMount(() => dialog.showModal());

  async function submit(event: SubmitEvent) {
    event.preventDefault();
    busy = true;
    error = undefined;
    try {
      error = await onmove(target);
      if (!error) {
        dialog.close();
      }
    } finally {
      busy = false;
    }
  }
</script>

<dialog bind:this={dialog} onclose={onclose} aria-labelledby="move-title">
  <form onsubmit={submit}>
    <h2 id="move-title">Move {subject}</h2>
    <fieldset class="choices">
      <legend class="muted">Choose a folder</legend>
      <label>
        <input type="radio" name="folder" checked={target === ''} onchange={() => (path = '')} />
        <span class="muted">No folder</span>
      </label>
      {#each choices as { node, depth } (node.path)}
        <label style:--depth={depth}>
          <input type="radio" name="folder" checked={target === node.path} onchange={() => (path = node.path)} />
          <span>{node.name}</span>
        </label>
      {/each}
    </fieldset>
    <label class="path">
      <span>Folder path</span>
      <input type="text" bind:value={path} maxlength="200" placeholder="No folder" aria-describedby="move-hint" />
    </label>
    <p id="move-hint" class="muted hint">
      {#if isNew}Creates the new folder “{target}”.{:else}Separate subfolders with “/”, for example village/houses.{/if}
    </p>
    {#if error}
      <p class="notice error" role="alert">{error}</p>
    {/if}
    <div class="dialog-actions">
      <button type="button" onclick={() => dialog.close()}>Cancel</button>
      <button type="submit" class="primary" disabled={busy}>{target === '' ? 'Move out of folders' : 'Move'}</button>
    </div>
  </form>
</dialog>

<style>
  .choices {
    max-height: 16rem;
    overflow-y: auto;
    margin: 0 0 1rem;
    padding: 0.4rem 0.6rem;
    border: 1px solid var(--border);
    border-radius: var(--radius);
  }

  .choices label {
    display: flex;
    align-items: center;
    gap: 0.4rem;
    padding: 0.15rem 0 0.15rem calc(var(--depth, 0) * 1rem);
  }

  .path {
    display: grid;
    gap: 0.3rem;
  }

  .hint {
    margin: 0.4rem 0 0;
    font-size: 0.85rem;
  }
</style>
