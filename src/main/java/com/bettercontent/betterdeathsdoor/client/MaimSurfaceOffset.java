package com.bettercontent.betterdeathsdoor.client;

import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Vector3f;

/** Model-space clearance for drawPosed's already-transformed vertices.
 * Native GUI avatars reflect/scale positions with mulPoseMatrix, which does NOT
 * update PoseStack's lighting-normal matrix. Lighting normals are therefore not
 * necessarily outward geometric normals and must not directly displace vertices.
 */
final class MaimSurfaceOffset {
    static final float MODEL_CLEARANCE = .002f;
    private final Matrix3f undoLightingNormal;
    private final Matrix3f geometricNormal;

    MaimSurfaceOffset(Matrix3fc geometry, Matrix3fc lightingNormal) {
        undoLightingNormal = new Matrix3f(lightingNormal).invert();
        geometricNormal = new Matrix3f(geometry).invert().transpose();
    }

    void offset(float nx, float ny, float nz, Vector3f target) {
        undoLightingNormal.transform(target.set(nx, ny, nz));
        double modelLength = Math.sqrt((double) target.x * target.x
                + (double) target.y * target.y + (double) target.z * target.z);
        geometricNormal.transform(target);
        double squared = (double) target.x * target.x
                + (double) target.y * target.y + (double) target.z * target.z;
        if (!(squared > 0) || !Double.isFinite(squared) || !Double.isFinite(modelLength)) {
            target.zero();
            return;
        }
        // Separation between transformed parallel planes, not a fixed GUI-pixel
        // offset. Keep the source lighting normal unchanged for native shading.
        target.mul((float) (MODEL_CLEARANCE * modelLength / squared));
        if (!Float.isFinite(target.x) || !Float.isFinite(target.y) || !Float.isFinite(target.z)) target.zero();
    }
}
