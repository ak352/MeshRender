attribute vec3 a_Position;
attribute vec3 a_Normal;

uniform mat4 u_MVPMatrix;
uniform mat4 u_MVMatrix;
uniform mat3 u_NormalMatrix;

varying vec3 v_Normal;
varying vec4 v_ViewPosition;
varying vec2 v_ScreenSpacePosition;

void main()
{
    v_ViewPosition = u_MVMatrix * vec4(a_Position, 1.0);
    gl_Position = u_MVPMatrix * vec4(a_Position, 1.0);
    vec3 ndc = gl_Position.xyz / gl_Position.w;
    v_ScreenSpacePosition = ndc.xy * 0.5 + 0.5;
    v_Normal = normalize(u_NormalMatrix * a_Normal);

}