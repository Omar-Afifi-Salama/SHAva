package com.framework.view;

enum BinaryUnit {
    B("B", 1L),
    KiB("KiB", 1L << 10),
    MiB("MiB", 1L << 20),
    GiB("GiB", 1L << 30);

    public final String label;
    public final long bytes;

    BinaryUnit(String label, long bytes) {
        this.label = label;
        this.bytes = bytes;
    }
}
