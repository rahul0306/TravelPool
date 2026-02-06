package com.example.travelpool.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryTtlCache<K : Any, V : Any>(
    private val ttlMs: Long
) {
    private data class Entry<V>(val value: V, val storedAtMs: Long)

    private val m = Mutex()
    private val map = LinkedHashMap<K, Entry<V>>(64, 0.75f, true)

    suspend fun get(key: K): V? = m.withLock {
        val e = map[key] ?: return@withLock null
        if (System.currentTimeMillis() - e.storedAtMs > ttlMs) {
            map.remove(key); return@withLock null
        }
        e.value
    }

    suspend fun put(key: K, value: V) = m.withLock {
        map[key] = Entry(value, System.currentTimeMillis())
        // light cleanup
        if (map.size > 200) {
            val iter = map.entries.iterator()
            repeat(50) {
                if (iter.hasNext()) iter.next().also { _ -> iter.remove() }
            }
        }
    }
}