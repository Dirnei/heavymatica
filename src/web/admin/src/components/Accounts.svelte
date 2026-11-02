<script lang="ts">
  import { onMount } from 'svelte';
  import { api, type AccountSummary } from '../lib/api';
  import { formatBytes, formatTime, providerName, shortId } from '../lib/format';
  import { accountUrl, accountsUrl } from '../lib/navigation';
  import { messageOf, offlineMessage } from '../lib/problems';
  import { loadSession } from '../lib/session';
  import Notice from './Notice.svelte';

  let ready = $state(false);
  let q = $state(new URLSearchParams(window.location.search).get('q') ?? '');
  let searched = $state<string>();
  let results = $state<AccountSummary[]>([]);
  let busy = $state(false);
  let error = $state<string>();

  onMount(async () => {
    try {
      ready = !!(await loadSession());
    } catch {
      error = offlineMessage;
      return;
    }
    if (ready && q.trim()) {
      await search();
    }
  });

  async function search() {
    const query = q.trim();
    if (!query) {
      return;
    }
    busy = true;
    error = undefined;
    try {
      const { data, error: problem } = await api.GET('/v1/accounts', { params: { query: { q: query } } });
      if (data) {
        results = data.accounts;
        searched = query;
        window.history.replaceState(null, '', accountsUrl(query));
      } else {
        error = messageOf(problem);
      }
    } catch {
      error = offlineMessage;
    }
    busy = false;
  }

  function submit(event: SubmitEvent) {
    event.preventDefault();
    void search();
  }
</script>

<section class="stack">
  <h1>Accounts</h1>

  {#if ready}
    <form class="card form" onsubmit={submit} role="search">
      <label class="field">
        <span>Find an account</span>
        <input type="search" bind:value={q} maxlength="64" required autocomplete="off" spellcheck="false" />
        <span class="hint">An account ID, a Minecraft UUID, a Minecraft name or a GitHub login, in any letter case.</span>
      </label>
      <div class="actions">
        <button type="submit" class="primary" disabled={busy || q.trim() === ''}>Search</button>
      </div>
    </form>
  {/if}

  <Notice message={error} />

  {#if searched !== undefined}
    {#if results.length === 0}
      <p class="notice" role="status">No account matches “{searched}”.</p>
    {:else}
      <p class="muted" role="status">
        {results.length === 1 ? '1 account' : `${results.length} accounts`} for “{searched}”{results.length >= 50
          ? ', the first 50 only'
          : ''}.
      </p>
      <div class="table-wrap card">
        <table>
          <thead>
            <tr><th>Account</th><th>Sign-ins</th><th>Storage</th><th>Created</th></tr>
          </thead>
          <tbody>
            {#each results as account (account.id)}
              <tr>
                <td><a class="mono" href={accountUrl(account.id)} title={account.id}>{shortId(account.id, 12)}</a></td>
                <td>
                  {#each account.identities as identity (identity.id)}
                    <div>
                      <span class="tag">{providerName(identity.provider)}</span>
                      {identity.name ?? identity.subject}
                    </div>
                  {/each}
                </td>
                <td>{formatBytes(account.usedBytes)} of {formatBytes(account.quotaBytes)}</td>
                <td>{formatTime(account.createdAt)}</td>
              </tr>
            {/each}
          </tbody>
        </table>
      </div>
    {/if}
  {/if}
</section>
