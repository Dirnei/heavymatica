<script lang="ts">
  import type { QueuedUpload } from '../lib/upload';

  interface Props {
    uploads: QueuedUpload[];
    ondismiss: () => void;
  }

  const { uploads, ondismiss }: Props = $props();

  const busy = $derived(uploads.some((upload) => upload.state === 'waiting' || upload.state === 'uploading'));
  const done = $derived(uploads.filter((upload) => upload.state === 'done').length);
</script>

<section class="queue card" aria-live="polite" aria-label="Uploads">
  <header>
    <strong>{busy ? `Uploading ${done + 1} of ${uploads.length}` : `${done} of ${uploads.length} uploaded`}</strong>
    {#if !busy}
      <button type="button" class="close" onclick={ondismiss} aria-label="Close uploads">×</button>
    {/if}
  </header>
  <ul>
    {#each uploads as upload (upload.key)}
      <li class={upload.state}>
        <span class="name" title={upload.folder ? `${upload.folder}/${upload.name}` : upload.name}>{upload.name}</span>
        {#if upload.state === 'uploading'}
          <span class="meter"><span style:width={`${Math.round(upload.progress * 100)}%`}></span></span>
        {:else if upload.state === 'done'}
          <span class="status">✓</span>
        {:else if upload.state === 'failed'}
          <span class="status message">{upload.message}</span>
        {:else}
          <span class="status muted">waiting</span>
        {/if}
      </li>
    {/each}
  </ul>
</section>

<style>
  .queue {
    position: fixed;
    right: 1rem;
    bottom: 1rem;
    z-index: 5;
    width: min(22rem, calc(100vw - 2rem));
    max-height: 40vh;
    overflow-y: auto;
    padding: 0.7rem 0.9rem;
    box-shadow: 0 6px 20px rgb(0 0 0 / 0.25);
  }

  header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 0.4rem;
  }

  .close {
    padding: 0 0.3rem;
    border: none;
    background: none;
    font-size: 1.1rem;
    line-height: 1;
  }

  ul {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 0.35rem;
  }

  li {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 0.6rem;
    align-items: center;
    font-size: 0.9rem;
  }

  .name {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .meter {
    width: 5rem;
  }

  .failed .message {
    grid-column: 1 / -1;
    color: var(--danger);
    font-size: 0.85rem;
  }

  .done .status {
    color: var(--accent);
  }
</style>
