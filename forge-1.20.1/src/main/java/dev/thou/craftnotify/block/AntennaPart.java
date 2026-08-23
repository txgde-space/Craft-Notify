package dev.thou.craftnotify.block;

import net.minecraft.util.StringRepresentable;

public enum AntennaPart implements StringRepresentable {
    BASE("base", 0),
    MIDDLE("middle", 1),
    TOP("top", 2);

    private final String serializedName;
    private final int offset;

    AntennaPart(String serializedName, int offset) {
        this.serializedName = serializedName;
        this.offset = offset;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public int offset() {
        return offset;
    }
}
