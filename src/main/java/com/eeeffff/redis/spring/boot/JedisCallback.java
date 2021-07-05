package com.eeeffff.redis.spring.boot;

/**
 * @author fenglibin
 */

public interface JedisCallback<T, E> {
    T doJedisCallbak(E e);
}

