package dev.thou.craftnotify.registry;

import dev.thou.craftnotify.CraftNotify;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, CraftNotify.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> CHARGE = sound("charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> BEAM = sound("beam");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUCCESS = sound("success");
    public static final DeferredHolder<SoundEvent, SoundEvent> FAIL = sound("fail");

    private ModSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(CraftNotify.id(name)));
    }

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }
}
