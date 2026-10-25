package com.ispengya.lightredis.store;

/**
 * 存储在 key 上的值。
 *
 * <p>每种数据类型对应一个实现，命令执行器通过类型判断拒绝错误的操作。</p>
 */
public interface RedisValue {

    /**
     * 返回数据类型。
     *
     * @return 数据类型
     */
    RedisType getType();

    /**
     * 判断值是否已经没有内容。
     *
     * <p>集合类值在删除最后一个元素后应返回 true，调用方据此删除 key。
     * 字符串即使内容为空也仍然表示 key 存在，因此始终返回 false。</p>
     *
     * @return 没有内容时返回 true
     */
    boolean isEmpty();
}
