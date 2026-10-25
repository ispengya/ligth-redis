package com.ispengya.lightredis.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

/**
 * 列表值。
 *
 * <p>底层使用 {@link LinkedList}，支持从两端高效插入和弹出。</p>
 */
public final class ListValue implements RedisValue {

    /**
     * 列表元素，索引 0 为表头。
     */
    private final LinkedList<String> elements = new LinkedList<>();

    @Override
    public RedisType getType() {
        return RedisType.LIST;
    }

    @Override
    public boolean isEmpty() {
        return elements.isEmpty();
    }

    /**
     * 返回列表长度。
     *
     * @return 元素数量
     */
    public int size() {
        return elements.size();
    }

    /**
     * 从表头插入元素。
     *
     * <p>参数按给定顺序依次插入表头，例如插入 a、b、c 后列表为 c、b、a。</p>
     *
     * @param values 要插入的元素
     * @return 插入后的列表长度
     */
    public int pushLeft(List<String> values) {
        for (String value : values) {
            elements.addFirst(value);
        }
        return elements.size();
    }

    /**
     * 从表尾插入元素。
     *
     * @param values 要插入的元素
     * @return 插入后的列表长度
     */
    public int pushRight(List<String> values) {
        for (String value : values) {
            elements.addLast(value);
        }
        return elements.size();
    }

    /**
     * 从表头弹出一个元素。
     *
     * @return 被弹出的元素，列表为空时返回 null
     */
    public String popLeft() {
        return elements.isEmpty() ? null : elements.removeFirst();
    }

    /**
     * 从表尾弹出一个元素。
     *
     * @return 被弹出的元素，列表为空时返回 null
     */
    public String popRight() {
        return elements.isEmpty() ? null : elements.removeLast();
    }

    /**
     * 返回指定索引的元素。
     *
     * @param index 索引，负数表示从表尾开始计数
     * @return 元素，索引越界时返回 null
     */
    public String get(int index) {
        int normalized = normalizeIndex(index);
        return normalized < 0 ? null : elements.get(normalized);
    }

    /**
     * 修改指定索引的元素。
     *
     * @param index 索引，负数表示从表尾开始计数
     * @param value 新值
     * @return 修改成功返回 true，索引越界返回 false
     */
    public boolean set(int index, String value) {
        int normalized = normalizeIndex(index);
        if (normalized < 0) {
            return false;
        }
        elements.set(normalized, value);
        return true;
    }

    /**
     * 返回指定区间内的元素，区间包含 start 和 stop。
     *
     * @param start 起始索引，负数表示从表尾开始计数
     * @param stop 结束索引，负数表示从表尾开始计数
     * @return 区间内的元素，区间无效时返回空列表
     */
    public List<String> range(int start, int stop) {
        int size = elements.size();
        if (size == 0) {
            return Collections.emptyList();
        }

        long startIndex = start < 0 ? (long) size + start : start;
        long stopIndex = stop < 0 ? (long) size + stop : stop;
        if (startIndex < 0) {
            startIndex = 0;
        }
        if (stopIndex < 0 || startIndex >= size || startIndex > stopIndex) {
            return Collections.emptyList();
        }
        if (stopIndex >= size) {
            stopIndex = size - 1;
        }

        List<String> result = new ArrayList<>((int) (stopIndex - startIndex + 1));
        ListIterator<String> iterator = elements.listIterator((int) startIndex);
        for (int i = (int) startIndex; i <= (int) stopIndex; i++) {
            result.add(iterator.next());
        }
        return result;
    }

    /**
     * 删除与指定值相等的元素。
     *
     * @param value 要删除的元素
     * @param count 大于 0 时从表头开始删除 count 个，小于 0 时从表尾开始删除，
     *              等于 0 时删除全部匹配元素
     * @return 实际删除的数量
     */
    public int remove(String value, int count) {
        int removed = 0;
        if (count >= 0) {
            Iterator<String> iterator = elements.iterator();
            while (iterator.hasNext() && (count == 0 || removed < count)) {
                if (value.equals(iterator.next())) {
                    iterator.remove();
                    removed++;
                }
            }
            return removed;
        }

        int limit = -count;
        ListIterator<String> iterator = elements.listIterator(elements.size());
        while (iterator.hasPrevious() && removed < limit) {
            if (value.equals(iterator.previous())) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    /**
     * 将可能为负数的索引转换为从 0 开始的索引。
     *
     * @param index 原始索引
     * @return 有效索引，越界时返回 -1
     */
    private int normalizeIndex(int index) {
        int size = elements.size();
        long normalized = index < 0 ? (long) size + index : index;
        if (normalized < 0 || normalized >= size) {
            return -1;
        }
        return (int) normalized;
    }
}
