package net.neoforged.binarypatcher;

public final class DiffOptions {
    /**
     * When diffing .class files, try to use the base class constant table as the prefix of the
     * patched class file constant pool to minimize patch size resulting from reordered constants.
     */
    private boolean optimizeConstantPool;

    public boolean isOptimizeConstantPool() {
        return optimizeConstantPool;
    }

    public void setOptimizeConstantPool(boolean optimizeConstantPool) {
        this.optimizeConstantPool = optimizeConstantPool;
    }
}
