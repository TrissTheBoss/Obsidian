package dev.obsidian;

import dev.obsidian.bootstrap.ObsidianBootstrap;
import net.fabricmc.api.ClientModInitializer;

public final class ObsidianClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ObsidianBootstrap.earlyInitialize();
    }
}
