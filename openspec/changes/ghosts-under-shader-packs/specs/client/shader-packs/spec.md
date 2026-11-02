# Spec Delta

## Purpose

Keeps placement ghosts, mistake boxes and placement outlines visible and working while a shader pack is in use, and
keeps the mod working with and without a shader mod installed.

## ADDED Requirements

### Requirement: Placement visuals under a shader pack
While a shader pack is in use, the client SHALL draw ghosts, mistake boxes and placement outlines on top of the shaded
scene, after the shader pack has finished. They SHALL show the same blocks, opacity, layers, in-hand filter and
world-comparison results as without a shader pack.

#### Scenario: Ghost visible with a shader pack
- **WHEN** Iris is installed with Complementary Unbound and Euphoria Patches active, and an anchored placement is in view
- **THEN** its ghost blocks and box outline are visible

#### Scenario: Locked placement with mistakes
- **WHEN** a locked placement with a wrong block is in view while a shader pack is in use
- **THEN** the mistake box of that block is visible, matching positions show no ghost and missing positions show the ghost

#### Scenario: Opacity and layers
- **WHEN** a shader pack is in use, the ghost opacity is 80% and the placement shows layers 3 to 5 only
- **THEN** the ghost is drawn at 80% opacity in layers 3 to 5 only

### Requirement: Ghosts unlit by the shader pack
Under a shader pack, ghosts SHALL keep the look they have without one: the pack's lighting, shadows, fog and
post-processing SHALL NOT apply to them, and they SHALL NOT cast shadows or show in reflections.

#### Scenario: Night with shaders
- **WHEN** it is night, a shader pack is in use and a ghost is in view
- **THEN** the ghost is drawn as it would be without a shader pack at the same time and place

### Requirement: Real blocks hide placement visuals under a shader pack
While a shader pack is in use, real blocks between the camera and a ghost or mistake box SHALL hide it, as they do
without a shader pack.

#### Scenario: Ghost behind a wall
- **WHEN** a shader pack is in use and a solid wall stands between the player and part of a ghost
- **THEN** that part of the ghost is not visible through the wall

### Requirement: Shader pack switched at runtime
The client SHALL follow whether a shader pack is in use from one frame to the next, without a restart: turning the
pack on or off, switching packs, or a pack failing to compile SHALL keep placement visuals visible.

#### Scenario: Pack turned off
- **WHEN** a placement is in view and the player turns the shader pack off in the Iris screen
- **THEN** the ghost stays visible and is drawn as without Iris from the next frame

#### Scenario: Pack fails to compile
- **WHEN** the selected shader pack fails to compile and Iris falls back to vanilla rendering
- **THEN** ghosts are drawn as without Iris

### Requirement: Shader mod is optional
The mod SHALL load and work without Iris installed, and with Iris installed but no shader pack selected; in both cases
placement visuals SHALL be drawn as without a shader mod.

#### Scenario: No Iris
- **WHEN** the client runs without Iris
- **THEN** the mod loads and ghosts are drawn as before

#### Scenario: Iris without a pack
- **WHEN** Iris is installed and shaders are turned off
- **THEN** ghosts are drawn as without Iris
