package com.jelly.farmhelperv2.mixin;

import com.jelly.farmhelperv2.fish.AutoFishing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.sound.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class SoundInterceptorMixin {

    @Inject(method = "onPlaySound", at = @At("HEAD"), require = 0)
    private void mteu$onPlaySound(PlaySoundS2CPacket packet, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!mc.isOnThread()) return;

        SoundEvent event = packet.getSound().value();
        String soundId = event.id().toString();
        AutoFishing.onSound(mc, soundId, packet.getX(), packet.getY(), packet.getZ());
    }
}
