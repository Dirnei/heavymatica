<script lang="ts">
  import { onMount } from 'svelte';
  import { api, type AccountOverview } from '../lib/api';
  import { auditLabel } from '../lib/audit';
  import { formatBytes, formatCount, formatTime, providerName, usagePercent } from '../lib/format';
  import { accountsPath, auditPath } from '../lib/navigation';
  import { messageOf, offlineMessage } from '../lib/problems';
  import { loadSession } from '../lib/session';
  import Notice from './Notice.svelte';
  import QuotaForm from './QuotaForm.svelte';

  const id = new URLSearchParams(window.location.search).get('id')?.trim() ?? '';

  let account = $state<AccountOverview>();
  let missing = $state(false);
  let error = $state<string>();

  const percent = $derived(account ? usagePercent(account.usedBytes, account.quotaBytes) : 0);

  onMount(async () => {
    if (!id) {
      missing = true;
      return;
    }
    try {
      if (!(await loadSession())) {
        return;
      }
      const { data, error: problem, response } = await api.GET('/v1/accounts/{accountId}', {
        params: { path: { accountId: id } },
      });
      if (data) {
        account = data;
      } else if (response.status === 404) {
        missing = true;
      } else {
        error = messageOf(problem);
      }
    } catch {
      error = offlineMessage;
    }
  });
</script>

<section class="stack">
  <p><a href={accountsPath}>← Account search</a></p>

  {#if missing}
    <h1>Account not found</h1>
    <p>There is no account with this ID. <a href={accountsPath}>Search for it</a> instead.</p>
  {:else if account}
    <h1>Account <span class="mono">{account.id}</span></h1>

    <div class="overview">
      <div class="card stack">
        <h2>Storage</h2>
        <p>{formatBytes(account.usedBytes)} of {formatBytes(account.quotaBytes)} used</p>
        <div class="meter" class:full={percent >= 95} title={`${percent.toFixed(0)} %`}>
          <span style:width={`${percent}%`}></span>
        </div>
        <dl>
          <dt>Schematics</dt>
          <dd>{formatCount(account.schematicCount)}</dd>
          <dt>Created</dt>
          <dd>{formatTime(account.createdAt)}</dd>
        </dl>
      </div>
      <QuotaForm {account} onchange={(next) => (account = next)} />
    </div>

    <div class="card stack">
      <h2>Sign-ins</h2>
      <div class="table-wrap">
        <table>
          <thead>
            <tr><th>Provider</th><th>Name</th><th>Subject</th><th>Linked</th><th>Last sign-in</th></tr>
          </thead>
          <tbody>
            {#each account.identities as identity (identity.id)}
              <tr>
                <td>{providerName(identity.provider)}</td>
                <td>{identity.name ?? '–'}</td>
                <td class="mono">{identity.subject}</td>
                <td>{formatTime(identity.linkedAt)}</td>
                <td>{formatTime(identity.lastSignInAt)}</td>
              </tr>
            {/each}
          </tbody>
        </table>
      </div>
    </div>

    <div class="card stack">
      <h2>Recent account activity</h2>
      {#if account.audit.length === 0}
        <p class="muted">Nothing recorded.</p>
      {:else}
        <div class="table-wrap">
          <table>
            <thead>
              <tr><th>Time</th><th>What</th><th>By</th><th>Sign-in</th></tr>
            </thead>
            <tbody>
              {#each account.audit as entry, index (index)}
                <tr>
                  <td>{formatTime(entry.at)}</td>
                  <td>{auditLabel(entry.kind)}</td>
                  <td class="mono">{entry.actor ?? '–'}</td>
                  <td class="mono">{entry.identityId ?? '–'}</td>
                </tr>
              {/each}
            </tbody>
          </table>
        </div>
      {/if}
      <p class="hint">Quota changes by admins are in the <a href={auditPath}>admin audit log</a>.</p>
    </div>
  {:else}
    <Notice message={error} />
    {#if !error}
      <p class="muted" aria-busy="true">Loading…</p>
    {/if}
  {/if}
</section>

<style>
  .overview {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(20rem, 1fr));
    gap: 1rem;
    align-items: start;
  }

  h1 .mono {
    font-size: 0.7em;
  }

  dl {
    display: grid;
    grid-template-columns: auto 1fr;
    gap: 0.3rem 1rem;
    margin: 0;
  }

  dt {
    color: var(--muted);
  }

  dd {
    margin: 0;
  }
</style>
