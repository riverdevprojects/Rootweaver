package com.example.examplemod;

import com.example.examplemod.api.debug.DebugTreePresets;
import com.example.examplemod.command.ArborCommand;
import com.example.examplemod.registry.ArborRegistries;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(ArborAPI.MOD_ID)
public final class ArborAPI {
    public static final String MOD_ID = "arborapi";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ArborAPI(IEventBus modEventBus) {
        ArborRegistries.init();
        modEventBus.addListener(this::onCommonSetup);
        NeoForge.EVENT_BUS.addListener(ArborCommand::register);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(DebugTreePresets::bootstrap);
    }
}
