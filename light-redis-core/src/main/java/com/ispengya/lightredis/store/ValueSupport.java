package com.ispengya.lightredis.store;

/**
 * 按类型读取存储值的工具类。
 *
 * <p>命令执行器通过这里获取具体类型；key 不存在时返回 null，
 * 类型不匹配时抛出 {@link WrongTypeException}。</p>
 */
public final class ValueSupport {

    private ValueSupport() {
    }

    /**
     * 读取字符串值。
     *
     * @param store 存储
     * @param key key
     * @return 字符串值，key 不存在时返回 null
     * @throws WrongTypeException key 不是字符串类型时抛出
     */
    public static StringValue string(KeyValueStore store, String key) {
        RedisValue value = typedValue(store, key, RedisType.STRING);
        return (StringValue) value;
    }

    /**
     * 读取列表值。
     *
     * @param store 存储
     * @param key key
     * @param create key 不存在时是否创建空列表
     * @return 列表值，key 不存在且不创建时返回 null
     * @throws WrongTypeException key 不是列表类型时抛出
     */
    public static ListValue list(KeyValueStore store, String key, boolean create) {
        RedisValue value = typedValue(store, key, RedisType.LIST);
        if (value == null && create) {
            ListValue created = new ListValue();
            store.set(key, created);
            return created;
        }
        return (ListValue) value;
    }

    /**
     * 读取哈希表值。
     *
     * @param store 存储
     * @param key key
     * @param create key 不存在时是否创建空哈希表
     * @return 哈希表值，key 不存在且不创建时返回 null
     * @throws WrongTypeException key 不是哈希表类型时抛出
     */
    public static HashValue hash(KeyValueStore store, String key, boolean create) {
        RedisValue value = typedValue(store, key, RedisType.HASH);
        if (value == null && create) {
            HashValue created = new HashValue();
            store.set(key, created);
            return created;
        }
        return (HashValue) value;
    }

    /**
     * 读取集合值。
     *
     * @param store 存储
     * @param key key
     * @param create key 不存在时是否创建空集合
     * @return 集合值，key 不存在且不创建时返回 null
     * @throws WrongTypeException key 不是集合类型时抛出
     */
    public static SetValue set(KeyValueStore store, String key, boolean create) {
        RedisValue value = typedValue(store, key, RedisType.SET);
        if (value == null && create) {
            SetValue created = new SetValue();
            store.set(key, created);
            return created;
        }
        return (SetValue) value;
    }

    /**
     * 读取有序集合值。
     *
     * @param store 存储
     * @param key key
     * @param create key 不存在时是否创建空有序集合
     * @return 有序集合值，key 不存在且不创建时返回 null
     * @throws WrongTypeException key 不是有序集合类型时抛出
     */
    public static ZSetValue zset(KeyValueStore store, String key, boolean create) {
        RedisValue value = typedValue(store, key, RedisType.ZSET);
        if (value == null && create) {
            ZSetValue created = new ZSetValue();
            store.set(key, created);
            return created;
        }
        return (ZSetValue) value;
    }

    /**
     * 读取值并校验类型。
     *
     * @param store 存储
     * @param key key
     * @param expected 期望类型
     * @return 值，key 不存在时返回 null
     * @throws WrongTypeException 类型不匹配时抛出
     */
    private static RedisValue typedValue(KeyValueStore store, String key, RedisType expected) {
        RedisValue value = store.get(key);
        if (value != null && value.getType() != expected) {
            throw new WrongTypeException();
        }
        return value;
    }
}
