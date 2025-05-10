#version 150 core

uniform vec2 screenSize;
in vec2 position;
in vec2 uv;
in vec4 color;
out vec2 fTex;
out vec4 fColour;

void main() {
    fTex = uv;
    fColour = color;
    gl_Position = vec4((position / screenSize) * 2 - 1, 0.0, 1.0);
}
