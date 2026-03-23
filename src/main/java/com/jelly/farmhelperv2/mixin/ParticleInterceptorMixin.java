package com.jelly.farmhelperv2.mixin;

import com.jelly.farmhelperv2.pests.VacuumParticleTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts every incoming {@link ParticleS2CPacket} handled by the client
 * and forwards the relevant data to {@link VacuumParticleTracker}.
 *
 * <h3>Threading note</h3>
 * {@code ClientPlayNetworkHandler.onParticle} is called twice for each
 * packet: once on the network thread (where
 * {@code NetworkThreadUtils.forceMainThread} immediately re-queues the work
 * and returns), and once on the main client thread where the work is
 * actually done.  The {@code mc.isOnThread()} guard discards the
 * network-thread invocation so we always observe stable MC state.
 *
 * <h3>Velocity semantics</h3>
 * When the packet's {@code count == 0} the client spawns exactly one
 * particle whose velocity is the raw offset vector from the packet —
 * this is the "directed particle" mode Hypixel uses for the vacuum trail.
 * When {@code count > 0} each particle gets a randomised velocity; we
 * pass {@code (0, 0)} for the velocity in that case and let
 * {@link VacuumParticleTracker} fall back to position-centroid strategy.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ParticleInterceptorMixin {

    @Inject(method = "onParticle", at = @At("HEAD"), require = 0)
    private void mteu$captureParticlePacket(ParticleS2CPacket packet, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();

        // Skip the network-thread invocation (forceMainThread not yet called).
        if (!mc.isOnThread()) return;
        if (mc.player == null) return;
        if (!VacuumParticleTracker.isActive()) return;

        // Extract velocity: only meaningful for directed (count==0) packets.
        double vx = 0, vz = 0;
        if (packet.getCount() == 0) {
            vx = packet.getOffsetX();
            vz = packet.getOffsetZ();
        }

        VacuumParticleTracker.onPacket(
                packet.getParameters(),
                packet.getX(), packet.getY(), packet.getZ(),
                vx, vz,
                mc.player.getX(), mc.player.getZ()
        );
    }
}
