package dev.thou.craftnotify.registry;

import dev.thou.craftnotify.CraftNotify;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, CraftNotify.MOD_ID);

    public static final RegistryObject<SoundEvent> CHARGE = sound("charge");
    public static final RegistryObject<SoundEvent> BEAM = sound("beam");
    public static final RegistryObject<SoundEvent> SUCCESS = sound("success");
    public static final RegistryObject<SoundEvent> FAIL = sound("fail");

    private ModSounds() {
    }

    private static RegistryObject<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(CraftNotify.id(name)));
    }

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }
}
