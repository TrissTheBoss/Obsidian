#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 repeatCoord;
flat in vec4 repeatBasis0;
flat in vec4 repeatBasis1;

out vec4 fragColor;

void main() {
    vec2 atlasBase = repeatBasis0.xy;
    vec2 dS = repeatBasis0.zw;
    vec2 dT = repeatBasis1.xy;
    vec2 extent = repeatBasis1.zw;

    vec2 unwrappedAtlas = atlasBase + repeatCoord.x * dS + repeatCoord.y * dT;
    vec2 local = repeatCoord - floor(repeatCoord);
    if (repeatCoord.x >= extent.x) local.x = 1.0;
    if (repeatCoord.y >= extent.y) local.y = 1.0;
    vec2 atlasCoord = atlasBase + local.x * dS + local.y * dT;

    vec4 color = textureGrad(
        Sampler0,
        atlasCoord,
        dFdx(unwrappedAtlas),
        dFdy(unwrappedAtlas)
    ) * vertexColor * ColorModulator;
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif
    fragColor = apply_fog(
        color,
        sphericalVertexDistance,
        cylindricalVertexDistance,
        FogEnvironmentalStart,
        FogEnvironmentalEnd,
        FogRenderDistanceStart,
        FogRenderDistanceEnd,
        FogColor
    );
}
