package infinitygroup.zlogic.compat.microtech;

import infinitygroup.zlogic.Config;
import infinitygroup.zlogic.compat.OptionalModCompat;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

public final class MicroTechCompat {
    public static final String MODID = "microtech";
    private static boolean registered;

    private MicroTechCompat() {
    }

    public static boolean isAvailable() {
        return Config.enableMicroTechCompat && OptionalModCompat.isLoaded(MODID);
    }

    public static void registerIfAvailable(IEventBus eventBus) {
        if (registered || eventBus == null || !isAvailable()) {
            return;
        }

        eventBus.addListener(MicroTechNoiseHandler::onServerTick);
        eventBus.addListener(MicroTechNoiseHandler::onLevelUnload);
        registered = true;
    }

    public static void registerIfAvailable() {
        registerIfAvailable(NeoForge.EVENT_BUS);
    }
}
