<script lang="ts">
  interface Props {
    tags: string[];
    /** Saves the full tag list and returns an error message when it failed. */
    onsave: (tags: string[]) => Promise<string | undefined>;
  }

  const { tags, onsave }: Props = $props();

  const maxTags = 20;
  const maxLength = 50;

  let draft = $state('');
  let error = $state<string>();
  let pending = Promise.resolve();

  /**
   * Saves one change after the other, each applied to the tags the previous save left, so the player can keep typing
   * while a save is still on its way.
   */
  function change(next: (current: string[]) => string[] | undefined) {
    pending = pending.then(async () => {
      const updated = next(tags);
      if (updated) {
        error = await onsave(updated);
      }
    });
  }

  function add(event: SubmitEvent) {
    event.preventDefault();
    const tag = draft.trim();
    draft = '';
    if (!tag) {
      return;
    }
    change((current) => {
      if (current.includes(tag)) {
        return undefined;
      }
      if (current.length >= maxTags) {
        error = `A schematic can have at most ${maxTags} tags.`;
        return undefined;
      }
      return [...current, tag];
    });
  }

  function remove(tag: string) {
    change((current) => current.filter((other) => other !== tag));
  }
</script>

<div class="tag-editor">
  <ul aria-label="Tags">
    {#each tags as tag (tag)}
      <li class="tag">
        {tag}
        <button type="button" class="remove" onclick={() => remove(tag)} aria-label={`Remove tag ${tag}`}>×</button>
      </li>
    {/each}
  </ul>
  <form onsubmit={add}>
    <input type="text" bind:value={draft} maxlength={maxLength} placeholder="Add tag" aria-label="Add tag" />
  </form>
  {#if error}
    <p class="notice error" role="alert">{error}</p>
  {/if}
</div>

<style>
  .tag-editor {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.4rem;
  }

  ul {
    display: contents;
    list-style: none;
  }

  .tag {
    display: inline-flex;
    align-items: center;
    gap: 0.2rem;
  }

  .remove {
    padding: 0 0.15rem;
    border: none;
    background: none;
    line-height: 1;
  }

  form input {
    width: 9rem;
    padding: 0.15rem 0.5rem;
    font-size: 0.85rem;
  }

  .notice {
    flex-basis: 100%;
    margin: 0.3rem 0 0;
  }
</style>
