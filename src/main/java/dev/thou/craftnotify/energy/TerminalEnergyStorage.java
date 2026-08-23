package dev.thou.craftnotify.energy;

import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.function.IntConsumer;

public final class TerminalEnergyStorage implements IEnergyStorage {
    private final int capacity;
    private final int maxReceive;
    private final IntConsumer changed;
    private int energy;

    public TerminalEnergyStorage(int capacity, int maxReceive, IntConsumer changed) {
        this.capacity = capacity;
        this.maxReceive = maxReceive;
        this.changed = changed;
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        int accepted = Math.min(capacity - energy, Math.min(maxReceive, Math.max(0, toReceive)));
        if (!simulate && accepted > 0) {
            energy += accepted;
            changed.accept(energy);
        }
        return accepted;
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        return 0;
    }

    public boolean consume(int amount) {
        if (amount <= 0 || energy < amount) {
            return false;
        }
        energy -= amount;
        changed.accept(energy);
        return true;
    }

    public void setEnergy(int energy) {
        this.energy = Math.clamp(energy, 0, capacity);
    }

    @Override public int getEnergyStored() { return energy; }
    @Override public int getMaxEnergyStored() { return capacity; }
    @Override public boolean canExtract() { return false; }
    @Override public boolean canReceive() { return true; }
}
