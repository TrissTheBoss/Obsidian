package dev.obsidian.mixin;

import dev.obsidian.bootstrap.ObsidianBootstrap;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftFrameMixin {
    @Inject(method = "renderFrame", at = @At("HEAD"))
    private void obsidian$beginFrame(boolean renderWorld, CallbackInfo ci) {
        ObsidianBootstrap.onFrameStart();
    }

    @Inject(method = "renderFrame", at = @At("RETURN"))
    private void obsidian$endFrame(boolean renderWorld, CallbackInfo ci) {
        ObsidianBootstrap.onFrameEnd();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void obsidian$shutdown(CallbackInfo ci) {
        ObsidianBootstrap.shutdown();
    }
}
