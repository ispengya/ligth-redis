package com.ispengya.lightredis.store;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于内存的 key-value 存储。
 *
 * <p>key 的增删使用并发 Map 保证可见性；具体值由命令执行线程串行修改。
 * 过期时间单独保存在 {@code expires} 中，所有方法都要求在同一命令线程调用。</p>
 */
public class InMemoryKeyValueStore implements KeyValueStore {

    /**
     * 所有 key 及其值。
     */
    private final ConcurrentMap<String, RedisValue> data = new ConcurrentHashMap<>();

    /**
     * key 的绝对过期时间，epoch 毫秒。
     */
    private final ConcurrentMap<String, Long> expires = new ConcurrentHashMap<>();

    /**
     * 因过期而删除的 key 数量。
     */
    private final AtomicLong expiredCount = new AtomicLong();

    /**
     * 定期删除使用的轮转迭代器，只在命令线程访问。
     */
    private Iterator<String> expireCursor;

    @Override
    public RedisValue get(String key) {
        if (key == null) {
            return null;
        }
        RedisValue value = data.get(key);
        if (value == null) {
            expires.remove(key);
            return null;
        }
        Long expireAt = expires.get(key);
        if (expireAt != null && now() >= expireAt) {
            removeExpired(key, expireAt);
            return null;
        }
        return value;
    }

    @Override
    public void set(String key, RedisValue value) {
        if (key == null) {
            throw new IllegalArgumentException("Key must not be null");
        }
        if (value == null) {
            throw new IllegalArgumentException("Value must not be null");
        }
        data.put(key, value);
        expires.remove(key);
    }

    @Override
    public void removeIfEmpty(String key, RedisValue value) {
        if (key == null || value == null) {
            return;
        }
        if (value.isEmpty() && data.remove(key, value)) {
            expires.remove(key);
        }
    }

    @Override
    public long del(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return 0L;
        }
        long count = 0L;
        long now = now();
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            RedisValue value = data.get(key);
            if (value == null) {
                expires.remove(key);
                continue;
            }
            Long expireAt = expires.get(key);
            if (expireAt != null && now >= expireAt) {
                removeExpired(key, expireAt);
                continue;
            }
            if (data.remove(key, value)) {
                expires.remove(key);
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean exists(String key) {
        return get(key) != null;
    }

    @Override
    public List<String> keys() {
        long now = now();
        List<String> result = new ArrayList<>(data.size());
        for (String key : data.keySet()) {
            Long expireAt = expires.get(key);
            if (expireAt == null || now < expireAt) {
                result.add(key);
            }
        }
        return result;
    }

    @Override
    public long size() {
        long now = now();
        long count = 0L;
        for (String key : data.keySet()) {
            Long expireAt = expires.get(key);
            if (expireAt == null || now < expireAt) {
                count++;
            }
        }
        return count;
    }

    @Override
    public String type(String key) {
        RedisValue value = get(key);
        return value == null ? "none" : value.getType().getCode();
    }

    @Override
    public boolean expire(String key, long ttlMillis) {
        if (key == null || get(key) == null) {
            return false;
        }
        if (ttlMillis <= 0) {
            removeKey(key);
            return true;
        }
        long now = now();
        long expireAt = ttlMillis > Long.MAX_VALUE - now ? Long.MAX_VALUE : now + ttlMillis;
        expires.put(key, expireAt);
        return true;
    }

    @Override
    public boolean expireAt(String key, long epochMillis) {
        if (key == null || get(key) == null) {
            return false;
        }
        if (epochMillis <= now()) {
            removeKey(key);
            return true;
        }
        expires.put(key, epochMillis);
        return true;
    }

    @Override
    public long pttl(String key) {
        RedisValue value = get(key);
        if (value == null) {
            return -2L;
        }
        Long expireAt = expires.get(key);
        if (expireAt == null) {
            return -1L;
        }
        long remaining = expireAt - now();
        if (remaining <= 0) {
            removeExpired(key, expireAt);
            return -2L;
        }
        return remaining;
    }

    @Override
    public boolean persist(String key) {
        if (key == null || get(key) == null) {
            return false;
        }
        return expires.remove(key) != null;
    }

    @Override
    public Long getExpireAt(String key) {
        if (key == null) {
            return null;
        }
        return expires.get(key);
    }

    @Override
    public int purgeExpired(int sampleLimit, long deadlineNanos) {
        if (sampleLimit <= 0) {
            return 0;
        }
        int removed = 0;
        do {
            long now = now();
            int sampled = 0;
            int expired = 0;
            while (sampled < sampleLimit) {
                String key = nextExpireKey();
                if (key == null) {
                    break;
                }
                sampled++;
                Long expireAt = expires.get(key);
                if (expireAt != null && now >= expireAt && removeExpired(key, expireAt)) {
                    expired++;
                }
            }
            removed += expired;
            if (expired * 4 <= sampled || System.nanoTime() >= deadlineNanos) {
                break;
            }
        } while (true);
        return removed;
    }

    @Override
    public long purgeAllExpired() {
        long now = now();
        long removed = 0L;
        Iterator<String> iterator = expires.keySet().iterator();
        while (iterator.hasNext()) {
            String key = iterator.next();
            Long expireAt = expires.get(key);
            if (expireAt != null && now >= expireAt && removeExpired(key, expireAt)) {
                removed++;
            }
        }
        expireCursor = null;
        return removed;
    }

    @Override
    public long getExpiredCount() {
        return expiredCount.get();
    }

    /**
     * 删除一个已经过期的 key。
     *
     * @param key key
     * @param expireAt 删除时观察到的过期时间
     * @return 实际删除值时返回 true
     */
    private boolean removeExpired(String key, long expireAt) {
        RedisValue value = data.get(key);
        if (value == null) {
            expires.remove(key, expireAt);
            return false;
        }
        if (data.remove(key, value)) {
            expires.remove(key, expireAt);
            expiredCount.incrementAndGet();
            return true;
        }
        return false;
    }

    /**
     * 删除 key 及其过期时间，不计入过期统计。
     *
     * @param key key
     */
    private void removeKey(String key) {
        data.remove(key);
        expires.remove(key);
    }

    /**
     * 返回轮转采样中的下一个带过期时间的 key。
     *
     * @return key；没有可采样的 key 时返回 null
     */
    private String nextExpireKey() {
        if (expireCursor == null || !expireCursor.hasNext()) {
            expireCursor = expires.keySet().iterator();
        }
        if (!expireCursor.hasNext()) {
            return null;
        }
        return expireCursor.next();
    }

    /**
     * 返回当前时间。
     *
     * @return epoch 毫秒
     */
    private long now() {
        return System.currentTimeMillis();
    }
}
