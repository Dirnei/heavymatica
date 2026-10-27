# Spec Delta

## Purpose
What admins can see and change about player accounts: finding an account, its overview, and its storage quota.

## ADDED Requirements

### Requirement: Finding accounts
An admin SHALL be able to find player accounts by account ID, Minecraft UUID, Minecraft name, or GitHub login. Name searches SHALL ignore letter case and return at most 50 accounts.

#### Scenario: By Minecraft name
- **WHEN** an admin searches for `steve`
- **THEN** the accounts holding a Minecraft identity named `Steve` are returned

#### Scenario: Nothing found
- **WHEN** an admin searches for a name no identity has
- **THEN** an empty result is returned

### Requirement: Account overview
An admin SHALL be able to see an account's ID, creation time, linked identities with their providers and names, storage usage, quota, number of schematics and its most recent account audit entries. The overview SHALL NOT include schematic files, thumbnails or download links.

#### Scenario: Overview
- **WHEN** an admin opens an account with two Minecraft identities and 12 schematics
- **THEN** the overview lists both identities, 12 schematics, the usage and the quota

### Requirement: Changing the quota
An admin SHALL be able to set an account's quota to a value from 0 to 100 GiB with a reason of 3 to 200 characters. A quota below the current usage SHALL be allowed: the account keeps its schematics and further uploads are rejected as over quota. Every change SHALL be recorded in the admin audit log with the old and the new quota and the reason.

#### Scenario: Raised quota
- **WHEN** an admin raises an account's quota from 100 MiB to 1 GiB with the reason "creator programme"
- **THEN** the account reports a quota of 1 GiB and the admin audit log holds the change with both values and the reason

#### Scenario: Quota below usage
- **WHEN** an admin sets the quota of an account using 40 MiB to 10 MiB
- **THEN** the account keeps all its schematics and its next upload is rejected as over quota

#### Scenario: No reason
- **WHEN** an admin changes a quota without a reason
- **THEN** the service rejects it and the quota is unchanged
