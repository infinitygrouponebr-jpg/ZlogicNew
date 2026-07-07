package infinitygroup.zlogic.compat;

import net.neoforged.fml.ModList;

public final class OptionalModCompat {
    private OptionalModCompat() {
    }

    public static boolean isLoaded(String modId) {
        return modId != null && !modId.isBlank() && ModList.get().isLoaded(modId);
    }
}
