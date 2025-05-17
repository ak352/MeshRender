precision mediump float;

varying vec3 v_Normal;

uniform vec3 u_LightDirection; //should be normalized in world or view space

void main()
{
    vec3 normal = normalize(v_Normal);
    float diffuse = max(dot(normal, normalize(u_LightDirection)), 0.0);

    vec3 baseColor = vec3(0.5);
    vec3 finalColor = baseColor * diffuse;

    gl_FragColor = vec4(finalColor, 1.0); //Orange teapot
}