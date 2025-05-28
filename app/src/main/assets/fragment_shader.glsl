precision mediump float;

varying vec3 v_Normal;
varying vec4 v_ViewPosition;
varying vec2 v_ScreenSpacePosition;


uniform vec3 u_LightDirection; //should be normalized in world or view space
uniform sampler2D u_DepthTexture;
uniform vec3 u_DepthUvTransform;

float DepthGetMillimeters(in sampler2D depth_texture, in vec2 depth_uv)
{
    vec4 packedDepthAndVisibility = texture2D(depth_texture, depth_uv);
    return packedDepthAndVisibility.r * 255.0 + packedDepthAndVisibility.a * 256.0 * 255.0;
//    return dot(packedDepthAndVisibility, vec2(1.0, 256.0));
}

float InverseLerp(in float between, in float lo, in float hi)
{
    return (between-lo)/(hi-lo);
}

float DepthGetVisibility(in sampler2D depth_texture, in vec2 depth_uv, in float asset_depth_mm)
{
    float depth_mm = DepthGetMillimeters(depth_texture, depth_uv);
    const float kDepthTolerancePerMm = 0.015f;
    float visibility_occlusion = clamp(0.5 * (depth_mm - asset_depth_mm) /
    (kDepthTolerancePerMm * asset_depth_mm) + 0.5, 0.0, 1.0);

    float visibility_depth_near = 1.0 - InverseLerp(depth_mm, 150.0, 200.0);
    float visibility_depth_far = InverseLerp(depth_mm, 7500.0, 8000.0);

    const float kOcclusionAlpha = 0.0f;
    float visibility = max(max(visibility_occlusion, kOcclusionAlpha), max(visibility_depth_near, visibility_depth_far));
    return visibility;
}


void main()
{

    vec3 normal = normalize(v_Normal);
    float diffuse = max(dot(normal, normalize(u_LightDirection)), 0.0);

    vec3 baseColor = vec3(0.5);
    vec3 finalColor = baseColor * diffuse;

    gl_FragColor = vec4(finalColor, 1.0); //Gray teapot



    const float kMetersToMillimeters = 1000.0;
    float asset_depth_mm = v_ViewPosition.z * kMetersToMillimeters * -1.;
    vec2 depth_uvs = vec3(v_ScreenSpacePosition.xy, 1).xy;
    float angle = radians(90.0);
    mat2 rotation = mat2(cos(angle), -sin(angle), sin(angle), cos(angle));
    vec2 center = vec2(0.5, 0.5);
    depth_uvs = rotation * (depth_uvs - center) + center;//todo- use Frame.transformCoordinates2D
//    vec2 depth_uvs = (u_DepthUvTransform * vec3(v_ScreenSpacePosition.xy, 1)).xy;
    gl_FragColor.a *= DepthGetVisibility(u_DepthTexture, depth_uvs, asset_depth_mm);


    //for debugging
//    float depth_mm = DepthGetMillimeters(u_DepthTexture, depth_uvs);
//    float minDepth = 150.0;
//    float maxDepth = 8000.0;
//    float normalizedDepth = clamp((depth_mm -  minDepth)/(maxDepth - minDepth), 0.0, 1.0);
//    gl_FragColor = vec4(vec3(depth_mm/10000.0), 1.0);
//    gl_FragColor = vec4(vec3(depth_mm), 1.0);
//    gl_FragColor = vec4(v_ScreenSpacePosition, 0.0, 1.0);


}