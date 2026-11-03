#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
// Ghosts are always fully lit, so the lightmap coordinates carry the vertex's cell index instead.
in ivec2 UV2;

uniform sampler2D Sampler2;
// One bit per cell, set while the cell's ghost shows.
uniform isamplerBuffer GhostMask;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    int cell = (UV2.x & 0xFFFF) | (UV2.y << 16);
    bool shown = (texelFetch(GhostMask, cell >> 3).r & (1 << (cell & 7))) != 0;
    vec3 pos = Position + ModelOffset;
    // Every vertex of a hidden cell lands on the same point outside the clip volume, so its quads are dropped.
    gl_Position = shown ? ProjMat * ModelViewMat * vec4(pos, 1.0) : vec4(2.0, 2.0, 2.0, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    vertexColor = Color * sample_lightmap(Sampler2, ivec2(240, 240));
    texCoord0 = UV0;
}
