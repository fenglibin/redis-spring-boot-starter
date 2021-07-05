package com.eeeffff.redis.spring.boot;

/**
 * @author fenglibin
 * @Title: ${file_name}
 * @Package ${package_name}
 * @Description:
 * @date 2017-11-03 22:15
 */

public interface JedisCallback<T, E> {
    T doJedisCallbak(E e);
}

