#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D tex;

layout(location = 0) in vec2 fTex;
layout(location = 1) in vec4 fColour;
layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = texture(tex, fTex) * fColour;
}
