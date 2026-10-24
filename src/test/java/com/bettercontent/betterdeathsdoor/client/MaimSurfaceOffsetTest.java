package com.bettercontent.betterdeathsdoor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaimSurfaceOffsetTest {
    private Vector3f displacement(Matrix3f geometry, Matrix3f lighting, Vector3f normal) {
        Vector3f result = new Vector3f();
        new MaimSurfaceOffset(geometry, lighting).offset(normal.x, normal.y, normal.z, result);
        return result;
    }

    @Test void nativeGuiPositionOnlyReflectionMustNotMoveMarksInsideTheSkin() {
        // InventoryScreen.mulPoseMatrix(scaling(scale, scale, -scale)) leaves
        // the lighting-normal matrix untouched. The old callback follows +Z
        // lighting and moves into a front surface whose geometric outside is -Z.
        for (float scale : new float[] {15, 30, 50, 100}) {
            Matrix3f geometry = new Matrix3f().scaling(scale, scale, -scale);
            Vector3f lightingNormal = new Vector3f(0, 0, 1);
            Vector3f actual = displacement(geometry, new Matrix3f(), lightingNormal);
            assertEquals(0, actual.x); assertEquals(0, actual.y);
            assertEquals(-MaimSurfaceOffset.MODEL_CLEARANCE * scale, actual.z, 1e-7f);
            assertTrue(actual.dot(lightingNormal) < 0, "Position-only reflection reverses geometric outside");
        }
    }

    @Test void actualNativePoseStackKeepsPositionOnlyGuiReflectionOutOfLightingNormals() {
        PoseStack pose = new PoseStack();
        pose.mulPoseMatrix(new Matrix4f().scaling(30, 30, -30));
        assertEquals(new Matrix3f(), pose.last().normal());
        Vector3f actual = displacement(new Matrix3f(pose.last().pose()), pose.last().normal(), new Vector3f(0, 0, 1));
        assertEquals(-.06f, actual.z, 1e-7f);
    }

    @Test void ordinaryWorldPoseRetainsTheExistingModelClearance() {
        Matrix3f geometry = new Matrix3f().rotateXYZ(.4f, -.6f, 1.1f);
        Matrix3f lighting = new Matrix3f(geometry).invert().transpose();
        Vector3f normal = lighting.transform(new Vector3f(.2f, .4f, -.8f).normalize());
        Vector3f expected = new Vector3f(normal).normalize().mul(MaimSurfaceOffset.MODEL_CLEARANCE);
        assertTrue(expected.equals(displacement(geometry, lighting, normal), 1e-7f));
        for (float magnitude : new float[] {.01f, 1, 20, 1000})
            assertTrue(expected.equals(displacement(geometry, lighting, new Vector3f(normal).mul(magnitude)), 1e-7f));
    }

    @Test void reflectedRotatedAndNonuniformPosesPreserveModelPlaneSeparation() {
        for (Matrix3f geometry : new Matrix3f[] {
                new Matrix3f(), new Matrix3f().scaling(-30, 30, 30),
                new Matrix3f().rotateXYZ(.3f, .8f, -.4f).scale(20, 30, -40)}) {
            for (Matrix3f lighting : new Matrix3f[] {new Matrix3f(), new Matrix3f().rotateXYZ(.7f, -.2f, .9f)}) {
                Vector3f modelNormal = new Vector3f(1, 2, 3).normalize();
                Vector3f renderedGeometricNormal = new Matrix3f(geometry).invert().transpose()
                        .transform(new Vector3f(modelNormal));
                Vector3f litNormal = lighting.transform(new Vector3f(modelNormal));
                Vector3f offset = displacement(geometry, lighting, litNormal);
                assertEquals(MaimSurfaceOffset.MODEL_CLEARANCE, offset.dot(renderedGeometricNormal), 1e-8f);
                assertTrue(offset.dot(renderedGeometricNormal) > 0);
            }
        }
    }

    @Test void degenerateOrNonfiniteInputsDoNotEmitNonfiniteVertices() {
        Matrix3f identity = new Matrix3f();
        assertEquals(new Vector3f(), displacement(identity, identity, new Vector3f()));
        assertEquals(new Vector3f(), displacement(identity, identity, new Vector3f(Float.NaN, 1, 0)));
        assertEquals(new Vector3f(), displacement(identity, identity, new Vector3f(0, Float.POSITIVE_INFINITY, 0)));
        assertEquals(new Vector3f(), displacement(new Matrix3f().zero(), identity, new Vector3f(1, 0, 0)));
        assertEquals(new Vector3f(), displacement(identity, new Matrix3f().zero(), new Vector3f(1, 0, 0)));
    }
}
