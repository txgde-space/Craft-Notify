package dev.thou.craftnotify.blockentity;

public enum NotificationStatus {
    UNCONFIGURED(0),
    DISABLED(0),
    MISSING_ANTENNA(1),
    NO_ENERGY(2),
    READY(3),
    COOLDOWN(7),
    SENDING(11),
    FAILED(15);

    private final int comparatorSignal;

    NotificationStatus(int comparatorSignal) {
        this.comparatorSignal = comparatorSignal;
    }

    public int comparatorSignal() {
        return comparatorSignal;
    }
}
