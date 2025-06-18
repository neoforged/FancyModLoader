#version 150 core
uniform sampler2D tex;
in vec2 fTex;
in vec4 fColour;
out vec4 fragColor;

void main() {
    fragColor = texture(tex, fTex) * fColour;
}
