package net.neoforged.jartransform;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ForkJoinPool;
import java.util.zip.Deflater;

final class DeflaterPool {
    private static final Deque<Deflater> deflaters = new ConcurrentLinkedDeque<>();

    private DeflaterPool() {
    }

    public static Deflater borrow() {
        Deflater deflater = deflaters.poll();
        if (deflater == null) {
            deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        }
        return deflater;
    }

    public static void release(Deflater deflater) {
        deflater.reset();
        if (deflaters.size() < ForkJoinPool.getCommonPoolParallelism()) {
            deflaters.add(deflater);
        } else {
            deflater.end();
        }
    }
}
