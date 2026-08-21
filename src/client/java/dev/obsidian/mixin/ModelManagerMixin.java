package dev.obsidian.mixin;

import dev.obsidian.render.terrain.SectionLifecycleEvents;
import net.minecraft.client.resources.model.ModelManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(ModelManager.class)
public abstract class ModelManagerMixin {
    @Inject(method = "reload", at = @At("RETURN"))
    private void obsidian$reloadCompleted(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        CompletableFuture<Void> future = cir.getReturnValue();
        if (future != null) {
            future.thenRun(SectionLifecycleEvents::resourceReloaded);
        }
    }
}
