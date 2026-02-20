#version 150

in vec4 vertexColor;
in vec2 texCoord0;

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float GameTime;

out vec4 fragColor;

void main() {
    // Simple discard shader for now
    // This will make anything rendered with this shader invisible
    discard;
}
