<script lang="ts">
  import { onMount } from 'svelte';
  import { api, type AdminAuditEntry } from '../lib/api';
  import { auditDetails, auditLabel } from '../lib/audit';
  import { formatTime } from '../lib/format';
  import { accountUrl } from '../lib/navigation';
  import { messageOf, offlineMessage } from '../lib/problems';
  import { requireAdmin } from '../lib/session';
  import Notice from './Notice.svelte';

  const pageSize = 50;

  let ready = $state(false);
  let entries = $state<AdminAuditEntry[]>([]);
  let cursor = $state<string | null>(null);
  let busy = $state(false);
  let error = $state<string>();

  onMount(async () => {
    try {
      if (!(await requireAdmin())) {
        return;
      }
    } catch {
      error = offlineMessage;
      return;
    }
    await load();
    ready = true;
  });

  async function load(next?: string) {
    busy = true;
    error = undefined;
    try {
      const { data, error: problem } = await api.GET('/v1/audit', {
        params: { query: { limit: pageSize, ...(next ? { cursor: next } : {}) } },
      });
      if (data) {
        const known = new Set(entries.map((entry) => entry.id));
        entries = [...entries, ...data.items.filter((entry) => !known.has(entry.id))];
        cursor = data.nextCursor ?? null;
      } else {
        error = messageOf(problem);
      }
    } catch {
      error = offlineMessage;
    }
    busy = false;
  }
</script>

<section class="stack">
  <h1>Audit log</h1>
  <p class="muted">Everything admins did and every failed attempt, newest first. Entries are kept for 400 days.</p>

  {#if ready}
    {#if entries.length > 0}
      <div class="table-wrap card">
        <table>
          <thead>
            <tr><th>Time</th><th>Admin</th><th>What</th><th>Details</th><th>IP address</th></tr>
          </thead>
          <tbody>
            {#each entries as entry (entry.id)}
              <tr>
                <td class="nowrap">{formatTime(entry.at)}</td>
                <td>{entry.adminName ?? entry.adminId ?? '–'}</td>
                <td>{auditLabel(entry.kind)}</td>
                <td>
                  {#if entry.target}<div class="mono">{entry.target}</div>{/if}
                  {#each auditDetails(entry.details) as detail (detail.key)}
                    <div>
                      <span class="muted">{detail.label}:</span>
                      {#if detail.key === 'accountId'}
                        <a class="mono" href={accountUrl(detail.value)}>{detail.value}</a>
                      {:else}
                        {detail.value}
                      {/if}
                    </div>
                  {/each}
                </td>
                <td class="mono">{entry.ip}</td>
              </tr>
            {/each}
          </tbody>
        </table>
      </div>
    {:else if !error}
      <p class="notice">Nothing recorded yet.</p>
    {/if}
  {/if}

  <Notice message={error} />

  {#if ready && cursor}
    <div class="actions">
      <button type="button" onclick={() => load(cursor ?? undefined)} disabled={busy}>Load more</button>
    </div>
  {:else if ready && entries.length > 0}
    <p class="muted">That is everything.</p>
  {:else if !ready && !error}
    <p class="muted" aria-busy="true">Loading…</p>
  {/if}
</section>

<style>
  .nowrap {
    white-space: nowrap;
  }
</style>
