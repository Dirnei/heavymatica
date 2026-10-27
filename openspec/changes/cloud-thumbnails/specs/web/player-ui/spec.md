# Spec Delta

## ADDED Requirements

### Requirement: Thumbnail appears after upload
After a player uploads a schematic or a version on the website, the library card and the details page SHALL show the version's thumbnail as soon as the service reports one, without a reload. Until then they SHALL show the placeholder.

#### Scenario: Rendered thumbnail shows up
- **WHEN** a player uploads `castle.litematic` and stays on the library page
- **THEN** the card of `castle` shows a placeholder at first and its thumbnail once it is rendered

#### Scenario: Rendering takes too long
- **WHEN** no thumbnail is reported within a minute of the upload
- **THEN** the card keeps the placeholder and shows the thumbnail after the next reload once it exists
