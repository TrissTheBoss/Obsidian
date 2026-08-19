package dev.obsidian.mixin;

import dev.obsidian.bootstrap.ObsidianBootstrap;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftBootstrapMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void obsidian$afterMinecraftConstructed(CallbackInfo ci) {
        ObsidianBootstrap.onMinecraftReady();
    }
}
