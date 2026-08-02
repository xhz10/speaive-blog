package com.speaive.blog.infrastructure.content;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

final class SlugLockRegistry {
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    <T> T withLock(String slug, Supplier<T> action) {
        // Studio writes are authenticated and the number of article slugs is bounded. Retaining the
        // lock avoids the unlock/remove ABA window where an old waiter and a new caller use different locks.
        ReentrantLock lock = locks.computeIfAbsent(slug, ignored -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    int trackedSlugCount() {
        return locks.size();
    }
}
