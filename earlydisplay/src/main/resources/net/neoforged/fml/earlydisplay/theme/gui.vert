#version 150 core

layout(std140) uniform screenSize {
    vec2 screenSizeVec;
};

in vec2 position;
in vec2 uv;
in vec4 color;
out vec2 fTex;
out vec4 fColour;

void main() {
    fTex = uv;
    fColour = color;
    gl_Position = vec4((position / screenSizeVec) * 2 - 1, 0.0, 1.0);
}
