package org.lytharalab.gfbs.morphe.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.lytharalab.gfbs.morphe.api.MorpheViewOptions;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stateful client telemetry sampler exposed to Lua as the read-only
 * {@code env} table. Angular and motion values are smoothed for HUD sway.
 */
final class MorpheEnvironment {
    private long tick;
    private double elapsed;
    private float previousYaw;
    private float previousPitch;
    private double smoothYawVelocity;
    private double smoothPitchVelocity;
    private double smoothSpeed;
    private boolean initialized;

    void gameTick() {
        tick++;
    }

    Map<String, ?> capture(Minecraft minecraft, MorpheViewOptions options, boolean interactive, double delta) {
        double safeDelta = Math.max(0, Math.min(0.25, delta));
        elapsed += safeDelta;
        LocalPlayer player = minecraft.player;
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        double mouseX = scaledMouseX(minecraft);
        double mouseY = scaledMouseY(minecraft);

        Map<String, Object> viewport = map(
            "width", width,
            "height", height,
            "aspect", height <= 0 ? 1.0 : width / (double) height,
            "gui_scale", minecraft.getWindow().getGuiScale(),
            "screen_width", minecraft.getWindow().getScreenWidth(),
            "screen_height", minecraft.getWindow().getScreenHeight()
        );
        Map<String, Object> input = map(
            "mouse_x", mouseX,
            "mouse_y", mouseY,
            "left_down", mouseButton(minecraft, GLFW.GLFW_MOUSE_BUTTON_LEFT),
            "right_down", mouseButton(minecraft, GLFW.GLFW_MOUSE_BUTTON_RIGHT),
            "middle_down", mouseButton(minecraft, GLFW.GLFW_MOUSE_BUTTON_MIDDLE),
            "cursor_locked", minecraft.mouseHandler.isMouseGrabbed(),
            "interactive", interactive
        );
        Map<String, Object> presentation = map(
            "surface", options.surface().name().toLowerCase(),
            "background", options.background().name().toLowerCase(),
            "input_mode", options.inputMode().name().toLowerCase(),
            "screen_layer", options.screenLayer().name().toLowerCase(),
            "priority", options.priority(),
            "hide_with_gui", options.hideWithGui()
        );

        Map<String, Object> result = map(
            "time", map(
                "tick", tick,
                "seconds", elapsed,
                "delta", safeDelta,
                "world_tick", minecraft.level == null ? 0L : minecraft.level.getGameTime(),
                "day_time", minecraft.level == null ? 0L : minecraft.level.getDayTime()
            ),
            "viewport", viewport,
            "input", input,
            "presentation", presentation,
            "game", map(
                "fps", minecraft.getFps(),
                "paused", minecraft.isPaused(),
                "hide_gui", minecraft.options.hideGui,
                "has_screen", minecraft.screen != null,
                "screen", minecraft.screen == null ? "" : minecraft.screen.getClass().getSimpleName()
            )
        );
        if (player == null || minecraft.level == null) {
            result.put("player", Map.of());
            result.put("camera", Map.of());
            result.put("world", Map.of());
            result.put("motion", Map.of());
            return result;
        }

        float yaw = player.getYRot();
        float pitch = player.getXRot();
        float yawDelta = initialized ? Mth.wrapDegrees(yaw - previousYaw) : 0;
        float pitchDelta = initialized ? pitch - previousPitch : 0;
        previousYaw = yaw;
        previousPitch = pitch;
        initialized = true;
        double angularDelta = Math.max(1.0E-4, safeDelta);
        double yawRate = yawDelta / angularDelta;
        double pitchRate = pitchDelta / angularDelta;
        double angularBlend = 1 - Math.exp(-12 * safeDelta);
        smoothYawVelocity = lerp(smoothYawVelocity, yawRate, angularBlend);
        smoothPitchVelocity = lerp(smoothPitchVelocity, pitchRate, angularBlend);

        Vec3 velocity = player.getDeltaMovement();
        double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        smoothSpeed = lerp(smoothSpeed, horizontalSpeed, 1 - Math.exp(-6 * safeDelta));
        double walkPhase = elapsed * (player.isSprinting() ? 15 : 10);
        double swayX = -smoothYawVelocity * 0.045 + player.input.leftImpulse * 1.5;
        double swayY = smoothPitchVelocity * 0.035 + Math.sin(walkPhase) * smoothSpeed * 18;
        double bobX = Math.cos(walkPhase * 0.5) * smoothSpeed * 12;
        double bobY = Math.abs(Math.sin(walkPhase)) * smoothSpeed * 11;

        result.put("player", map(
            "x", player.getX(),
            "y", player.getY(),
            "z", player.getZ(),
            "velocity_x", velocity.x,
            "velocity_y", velocity.y,
            "velocity_z", velocity.z,
            "speed", velocity.length(),
            "horizontal_speed", horizontalSpeed,
            "yaw", yaw,
            "pitch", pitch,
            "health", player.getHealth(),
            "max_health", player.getMaxHealth(),
            "absorption", player.getAbsorptionAmount(),
            "food", player.getFoodData().getFoodLevel(),
            "saturation", player.getFoodData().getSaturationLevel(),
            "air", player.getAirSupply(),
            "armor", player.getArmorValue(),
            "experience", player.experienceProgress,
            "level", player.experienceLevel,
            "on_ground", player.onGround(),
            "in_water", player.isInWater(),
            "sprinting", player.isSprinting(),
            "crouching", player.isCrouching(),
            "swimming", player.isSwimming(),
            "fall_flying", player.isFallFlying(),
            "riding", player.getVehicle() != null,
            "move_forward", player.input.forwardImpulse,
            "move_strafe", player.input.leftImpulse,
            "jumping", player.input.jumping,
            "shift", player.input.shiftKeyDown
        ));
        result.put("camera", map(
            "yaw", yaw,
            "pitch", pitch,
            "yaw_delta", yawDelta,
            "pitch_delta", pitchDelta,
            "yaw_velocity", smoothYawVelocity,
            "pitch_velocity", smoothPitchVelocity,
            "fov", minecraft.options.fov().get(),
            "first_person", minecraft.options.getCameraType().isFirstPerson()
        ));
        result.put("motion", map(
            "speed", smoothSpeed,
            "sway_x", swayX,
            "sway_y", swayY,
            "bob_x", bobX,
            "bob_y", bobY,
            "walk_phase", walkPhase,
            "moving", horizontalSpeed > 0.005,
            "airborne", !player.onGround()
        ));
        result.put("world", map(
            "dimension", minecraft.level.dimension().location().toString(),
            "biome", minecraft.level.getBiome(player.blockPosition()).unwrapKey()
                .map(key -> key.location().toString())
                .orElse(""),
            "raining", minecraft.level.isRaining(),
            "thundering", minecraft.level.isThundering(),
            "sky_darkness", minecraft.level.getSkyDarken(),
            "difficulty", minecraft.level.getDifficulty().getKey()
        ));
        return result;
    }

    static double scaledMouseX(Minecraft minecraft) {
        int screenWidth = minecraft.getWindow().getScreenWidth();
        return screenWidth <= 0 ? 0 : minecraft.mouseHandler.xpos()
            * minecraft.getWindow().getGuiScaledWidth() / screenWidth;
    }

    static double scaledMouseY(Minecraft minecraft) {
        int screenHeight = minecraft.getWindow().getScreenHeight();
        return screenHeight <= 0 ? 0 : minecraft.mouseHandler.ypos()
            * minecraft.getWindow().getGuiScaledHeight() / screenHeight;
    }

    private static boolean mouseButton(Minecraft minecraft, int button) {
        return GLFW.glfwGetMouseButton(minecraft.getWindow().getWindow(), button) == GLFW.GLFW_PRESS;
    }

    private static double lerp(double current, double target, double amount) {
        return current + (target - current) * amount;
    }

    private static LinkedHashMap<String, Object> map(Object... entries) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < entries.length; index += 2) {
            result.put(String.valueOf(entries[index]), entries[index + 1]);
        }
        return result;
    }
}
