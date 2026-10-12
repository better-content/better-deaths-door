package com.bettercontent.betterdeathsdoor.client;

import com.bettercontent.betterdeathsdoor.state.Region;

/** Shared anatomical names from Epic Fight 20.14.17's HumanoidMesh, not joint guesses. */
final class MaimSurface {
    private MaimSurface() {}

    static boolean supportsEpic(String version) {
        return "20.14.17".equals(version);
    }

    // Existing marks occupy base player UV islands. Clothing geometry uses a
    // separate half of that same 64x64 atlas; map it back without moving vertices.
    static float skinU(Region region, float u, boolean outer) {
        if (!outer) return u;
        return u + switch (region) {
            case HEAD -> -.5f;
            case LEFT_ARM -> -.25f;
            case LEFT_LEG -> .25f;
            default -> 0f;
        };
    }

    static float skinV(Region region, float v, boolean outer) {
        if (!outer) return v;
        return v + switch (region) {
            case TORSO, RIGHT_ARM, RIGHT_LEG -> -.25f;
            default -> 0f;
        };
    }

    // Native vanilla armor uses a 64x32 atlas. Helmet's outer hat is translated
    // to the existing head mark island; both arms/legs share vanilla armor UVs.
    static float armorU(Region region, float u) {
        return region == Region.HEAD && u >= .5f ? u - .5f : u;
    }

    static float armorV(float v) { return v * .5f; }

    static Region dominantRegion(float[] weights) {
        Region dominant = null;
        float maximum = 0;
        for (Region candidate : Region.values()) {
            if (weights[candidate.ordinal()] > maximum) {
                maximum = weights[candidate.ordinal()];
                dominant = candidate;
            }
        }
        return dominant;
    }

    static Region jointRegion(String joint) {
        return switch (joint) {
            case "Head" -> Region.HEAD;
            case "Root", "Torso", "Chest" -> Region.TORSO;
            case "Shoulder_L", "Arm_L", "Hand_L", "Elbow_L", "Tool_L" -> Region.LEFT_ARM;
            case "Shoulder_R", "Arm_R", "Hand_R", "Elbow_R", "Tool_R" -> Region.RIGHT_ARM;
            case "Thigh_L", "Leg_L", "Knee_L" -> Region.LEFT_LEG;
            case "Thigh_R", "Leg_R", "Knee_R" -> Region.RIGHT_LEG;
            default -> null;
        };
    }

    static String part(Region region) {
        return switch (region) {
            case HEAD -> "head";
            case TORSO -> "torso";
            case LEFT_ARM -> "leftArm";
            case RIGHT_ARM -> "rightArm";
            case LEFT_LEG -> "leftLeg";
            case RIGHT_LEG -> "rightLeg";
        };
    }

    static String outerPart(Region region) {
        return switch (region) {
            case HEAD -> "hat";
            case TORSO -> "jacket";
            case LEFT_ARM -> "leftSleeve";
            case RIGHT_ARM -> "rightSleeve";
            case LEFT_LEG -> "leftPants";
            case RIGHT_LEG -> "rightPants";
        };
    }
}
