package net.neoforged.binarypatcher;

import java.util.function.Predicate;

public final class DiffOptions {
    /**
     * When diffing .class files, try to use the base class constant table as the prefix of the
     * patched class file constant pool to minimize patch size resulting from reordered constants.
     */
    private boolean optimizeConstantPool;

    /**
     * Optional predicate that will be tested against every path from the base and modified jar to determine whether
     * the path participates in diffing.
     */
    private Predicate<String> pathFilter = path -> true;

    public boolean isOptimizeConstantPool() {
        return optimizeConstantPool;
    }

    public void setOptimizeConstantPool(boolean optimizeConstantPool) {
        this.optimizeConstantPool = optimizeConstantPool;
    }

    public Predicate<String> getPathFilter() {
        return pathFilter;
    }

    public void setPathFilter(Predicate<String> pathFilter) {
        this.pathFilter = pathFilter;
    }
}
