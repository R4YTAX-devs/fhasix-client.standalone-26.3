#version 330
#extension GL_ARB_separate_shader_objects : require

// Fhasix Motion Blur (Minecraft 26.3). 26.3 compiles every shader with ShaderC (OpenGL and Vulkan alike), so every
// input and output needs an explicit location and #version must stay the first line.

uniform sampler2D InSampler;
uniform sampler2D PrevSampler;

layout(std140) uniform FhasixBlur {
    float BlendFactor;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

// One 8-bit step. Blending in an 8-bit buffer rounds tiny differences to nothing and would leave a faint ghost of
// the old frame on screen forever, so a pixel that did not move is always pushed at least one step to the new frame.
const float LSB = 1.0 / 255.0;

void main() {
    vec3 current = texture(InSampler, texCoord).rgb;
    vec3 previous = texture(PrevSampler, texCoord).rgb;
    float blend = clamp(BlendFactor, 0.0, 0.95);

    // mix in a gamma-like space so bright things keep their brightness while they fade
    vec3 mixed = sqrt(mix(current * current, previous * previous, blend));

    vec3 delta = current - previous;
    vec3 stuck = 1.0 - step(0.5 * LSB, abs(mixed - previous));
    vec3 nudged = previous + sign(delta) * min(abs(delta), vec3(LSB));

    fragColor = vec4(mix(mixed, nudged, stuck), 1.0);
}
