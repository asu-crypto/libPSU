package edu.alibaba.mpc4j.psu.api.plugin;

/**
 * Common initialization parameters for plugins.
 */
public class PsuPluginInit {
    private final int maxSmallSetSize;
    private final int maxLargeSetSize;
    private final int elementByteLength;

    public PsuPluginInit(int maxSmallSetSize, int maxLargeSetSize, int elementByteLength) {
        this.maxSmallSetSize = maxSmallSetSize;
        this.maxLargeSetSize = maxLargeSetSize;
        this.elementByteLength = elementByteLength;
    }

    public int getMaxSmallSetSize() {
        return maxSmallSetSize;
    }

    public int getMaxLargeSetSize() {
        return maxLargeSetSize;
    }

    public int getElementByteLength() {
        return elementByteLength;
    }
}
