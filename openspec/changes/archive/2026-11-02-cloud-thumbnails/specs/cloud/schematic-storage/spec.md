# Spec Delta

## MODIFIED Requirements

### Requirement: Thumbnails
The service SHALL let an owner attach one PNG thumbnail of at most 256 x 256 pixels and 256 KiB to a version, once. An attached thumbnail SHALL replace a thumbnail the service rendered for that version. A version without a thumbnail SHALL remain fully usable.

#### Scenario: Thumbnail attached
- **WHEN** an owner attaches a valid PNG to a version that has none
- **THEN** the version reports a thumbnail and a download link for it can be requested

#### Scenario: Rendered thumbnail replaced
- **WHEN** an owner attaches a valid PNG to a version whose thumbnail the service rendered
- **THEN** the version's thumbnail is the attached PNG and the version reports it as uploaded

#### Scenario: Thumbnail rejected
- **WHEN** an owner attaches a file that is not a PNG, exceeds the size limits, or targets a version that already has an attached thumbnail
- **THEN** the service rejects it and the version is unchanged

### Requirement: Version metadata
The service SHALL expose for each version: version number, content hash, source format, size in bytes, dimensions, Minecraft data version, total block count, block counts per block type, upload time, whether a thumbnail exists, and whether it was attached by the uploader or rendered by the service.

#### Scenario: Metadata after upload
- **WHEN** a player uploads a 10 x 5 x 8 schematic containing 120 oak planks and 40 glass blocks
- **THEN** the new version reports dimensions 10 x 5 x 8, 160 blocks, and counts of 120 oak planks and 40 glass

#### Scenario: Thumbnail source
- **WHEN** a version's thumbnail was rendered by the service
- **THEN** the version reports a thumbnail and that it was rendered

## ADDED Requirements

### Requirement: Thumbnails rendered by the service
The service SHALL render a 256 x 256 PNG thumbnail for every version that has no thumbnail: an isometric view of the version's blocks, coloured per block type, with blocks of unknown types in a neutral colour. A version uploaded from the website SHALL be rendered within a minute. A version uploaded from the game SHALL first wait a grace period of one minute, in which the uploader can attach its own thumbnail. A version whose rendering fails SHALL stay usable without a thumbnail.

#### Scenario: Website upload
- **WHEN** a player uploads `castle.litematic` on the website
- **THEN** within a minute the new version reports a rendered thumbnail that shows the structure

#### Scenario: Game upload with its own thumbnail
- **WHEN** the game uploads a version and attaches its thumbnail within the grace period
- **THEN** the service renders nothing for that version and it keeps the uploaded thumbnail

#### Scenario: Game upload without a thumbnail
- **WHEN** the game uploads a version and attaches no thumbnail
- **THEN** after the grace period the version reports a rendered thumbnail

#### Scenario: Only air
- **WHEN** a version holds nothing but air and structure voids
- **THEN** the service stores no thumbnail for it and the version stays usable

#### Scenario: Unknown block
- **WHEN** a version holds blocks of a type the service has no colour for
- **THEN** those blocks are drawn in a neutral colour and the thumbnail is still made

### Requirement: Rendered thumbnails and the quota
Rendered thumbnails SHALL count toward the account's storage usage, but SHALL NOT be refused for the quota.

#### Scenario: Account at its quota
- **WHEN** a version of an account whose usage is at its quota is rendered
- **THEN** the version reports a rendered thumbnail and the usage grows by its size

### Requirement: Thumbnails for existing versions
Versions that existed without a thumbnail before the service rendered thumbnails SHALL be rendered in the background. New uploads SHALL be rendered before older versions. A version whose rendering failed three times SHALL NOT be tried again.

#### Scenario: Backfill
- **WHEN** the service starts with versions that have no thumbnail
- **THEN** each of them eventually reports a rendered thumbnail, unless rendering it fails

#### Scenario: New uploads first
- **WHEN** a website upload arrives while older versions are still waiting to be rendered
- **THEN** the new version is rendered within a minute
