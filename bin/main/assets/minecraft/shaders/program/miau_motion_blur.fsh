#version 120

uniform sampler2D DiffuseSampler;
uniform sampler2D PrevSampler;

varying vec2 texCoord;

uniform float Weight = 1.0;

void main() {
    vec4 currTexel = texture2D(DiffuseSampler, texCoord);
    vec4 prevTexel = texture2D(PrevSampler, texCoord);

    gl_FragColor = vec4(mix(prevTexel.rgb, currTexel.rgb, Weight), 1.0);
}
