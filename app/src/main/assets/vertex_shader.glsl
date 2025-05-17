attribute vec3 a_Position;
attribute vec3 a_Normal;

uniform mat4 u_MVPMatrix;
uniform mat3 u_NormalMatrix;

varying vec3 v_Normal;

void main()
{
    gl_Position = u_MVPMatrix * vec4(a_Position, 1.0);
    v_Normal = normalize(u_NormalMatrix * a_Normal);
}