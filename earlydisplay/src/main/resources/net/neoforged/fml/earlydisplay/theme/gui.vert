#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform screenSize {
    vec2 screenSizeVec;
};

layout(location = 0) in vec2 position;
layout(location = 1) in vec2 uv;
layout(location = 2) in vec4 color;
layout(location = 0) out vec2 fTex;
layout(location = 1) out vec4 fColour;

void main() {
    fTex = uv;
    fColour = color;
    gl_Position = vec4((position / screenSizeVec) * 2 - 1, 0.0, 1.0) * vec4(1.0, -1.0, 1.0, 1.0);
}
