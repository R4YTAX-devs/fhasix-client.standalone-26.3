#version 330
#extension GL_ARB_separate_shader_objects : require

// Plain copy used by the Fhasix Motion Blur chain (own file, so the chain does not depend on how the vanilla blit
// shader is declared in a given Minecraft version).

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = vec4(texture(InSampler, texCoord).rgb, 1.0);
}
