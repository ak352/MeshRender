# Real-time Occlusion Rendering
This demo shows how to use ARCore's Depth API for occlusion rendering in real-time. A 3D teapot mesh is rendered such that it appears behind real-world geometry using single-frame depth estimation from the ARCore SDK.

- Depth is **not integrated across frames** — this is a minimal, focused implementation
- The purpose is to demonstrate clean occlusion without relying on plane detection or complex scene understanding

## Demo
[Video on project page](https://ak352.github.io/#meshrender-demo)

## Lessons Learned

- ARCore depth and pose access require an active GL context. This means the usual `ImageAnalysis` thread doesn't work — depth access must happen in a coroutine tied to the GL rendering thread.
- ARCore camera feed is exclusive — cannot use CameraX alongside ARCore.
- A recomposing Jetpack Compose setup will recreate `Session` unless it is remembered — which breaks texture attachments and depth acquisition.
- Correct occlusion rendering requires exact matching of `glViewport` and display geometry, especially across screen rotations. Otherwise, the mesh appears squished or misaligned.
- Visual alignment between the depth map and RGB frame was verified manually. Frame-to-depth transformation was skipped in favor of a fixed rotation for simplicity.

---

**Keywords:** ARCore, Android, Kotlin, OpenGL ES, Depth Map, Pose Estimation, Occlusion, 3D Rendering, Augmented Reality, Jetpack Compose, Real-Time Graphics, Teapot Mesh, AR Visualization, Mobile AR
