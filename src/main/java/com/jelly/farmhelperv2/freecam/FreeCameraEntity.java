package com.jelly.farmhelperv2.freecam;

import com.jelly.farmhelperv2.config.ModConfig;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/**
 * Client-only camera body: noclip flight driven by {@link KeyboardInput}; does not affect the server player.
 */
public final class FreeCameraEntity extends AbstractClientPlayerEntity {

    /** Reads movement keys from game options while the real player's input is frozen. */
    public final KeyboardInput keyboardInput;

    public FreeCameraEntity(ClientWorld world, MinecraftClient client) {
        super(world, new GameProfile(UUID.randomUUID(), "MTEU_Freecam"));
        this.keyboardInput = new KeyboardInput(client.options);
        this.noClip = true;
        PlayerAbilities abilities = getAbilities();
        abilities.flying = true;
        abilities.allowFlying = true;
        setPose(EntityPose.SWIMMING);
    }

    @Override
    public void tick() {
        keyboardInput.tick();
        applyFreeMotion();
        super.tick();
    }

    private void applyFreeMotion() {
        double h = ModConfig.getFreecamHorizontalSpeed();
        double v = ModConfig.getFreecamVerticalSpeed();
        PlayerInput pi = keyboardInput.playerInput;
        if (pi.sprint()) {
            h *= 1.5;
            v *= 1.5;
        }

        Vec2f mv = keyboardInput.getMovementInput();
        float yawRad = (float) Math.toRadians(getYaw());
        float sin = (float) Math.sin(yawRad);
        float cos = (float) Math.cos(yawRad);
        double dx = (mv.x * cos - mv.y * sin) * h;
        double dz = (mv.y * cos + mv.x * sin) * h;
        double dy = 0.0;
        if (pi.jump()) {
            dy += v;
        }
        if (pi.sneak()) {
            dy -= v;
        }

        setVelocity(Vec3d.ZERO);
        // Ensure noclip/flying remain enabled in case something else toggles them.
        this.noClip = true;
        PlayerAbilities abilities = getAbilities();
        abilities.allowFlying = true;
        abilities.flying = true;
        // Teleport the camera each tick to bypass collision checks so the freecam can pass through blocks.
        // Using refreshPositionAndAngles updates position/angles without invoking normal collision resolution.
        refreshPositionAndAngles(getX() + dx, getY() + dy, getZ() + dz, getYaw(), getPitch());
        setOnGround(false);
        getAbilities().flying = true;
    }

    @Override
    public boolean isSpectator() {
        return false;
    }

    @Override
    public boolean isInvisible() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}
