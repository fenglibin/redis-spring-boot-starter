package com.eeeffff.redis.spring.boot;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.util.CollectionUtils;

import com.eeeffff.common.utils.DateTimeUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * RedisTemplate包装器。 <br>
 * 注：与RedisTemplateWrapper的区别在于，GlabalRedisTemplateWrapper操作的所有Key不会包含该当前应用的应用名称前缀，
 * 如当前应用为app1，某个key为key1，则其实际写入到redis中的key为"key1"。
 * 
 * @author fenglibin
 *
 */
@Slf4j
public class RedisTemplateWrapper {
	private static RedisTemplate<String, Object> redisTemplate;

	// Redis锁的最大生存时间，单位为毫秒
	private static int lockExpireTime = 60000;

	public static RedisTemplate<String, Object> getRedisTemplate() {
		return redisTemplate;
	}

	public static void setRedisTemplate(RedisTemplate<String, Object> redisTemplate) {
		RedisTemplateWrapper.redisTemplate = redisTemplate;
	}

	/**
	 * 根据key从redis中获取值
	 * 
	 * @param key
	 * @return
	 */
	public static Object get(String key) {
		return redisTemplate.opsForValue().get(getDefineKey(key));
	}

	/**
	 * 根据key获取到值后，并通过自定义实现的Consumer消费该key对应的内容
	 * 
	 * @param key
	 * @param valueConsumer
	 */
	public static void getAndConsumeValue(String key, Consumer<Object> valueConsumer) {
		Object value = get(key);
		valueConsumer.accept(value);
	}

	/**
	 * 将值写入到redis中
	 * 
	 * @param key
	 * @param value
	 */
	public static void set(String key, Object value) {
		redisTemplate.opsForValue().set(getDefineKey(key), value);
	}

	/**
	 * 将值写入到redis中，并指定过期时间
	 * 
	 * @param key
	 * @param value
	 * @param timeout
	 * @param unit
	 */
	public static void set(String key, Object value, long timeout, TimeUnit unit) {
		redisTemplate.opsForValue().set(getDefineKey(key), value, timeout, unit);
	}

	/**
	 * 从Redis中获取锁，获取成功返厍true，获取失败返回false，默认每次尝试的间隔时间为10毫秒。<br>
	 * 注：需要手动执行delLock删除锁定Key，否则会一直保留
	 * 
	 * @param lockKey       设置的锁
	 * @param maxWaitMillis 如果获取锁不成功，最多的等待时间，单位为毫秒
	 * @return
	 */
	public static boolean getLock(String lockKey, long maxWaitMillis) {
		long tryTimeInterval = 10;
		return getLock(lockKey, maxWaitMillis, tryTimeInterval);
	}

	/**
	 * 从Redis中获取锁，获取成功返厍true，获取失败返回false。<br>
	 * 注：需要手动执行delLock删除锁定Key，否则会一直保留
	 * 
	 * @param lockKey         设置的锁
	 * @param maxWaitMillis   如果获取锁不成功，最多的等待时间，单位为毫秒
	 * @param tryTimeInterval 每次获取锁的间隔时间
	 * @return
	 */
	public static boolean getLock(String lockKey, long maxWaitMillis, long tryTimeInterval) {
		boolean lock = getLock(lockKey);
		long currentWaitMillis = 0;
		while (!lock && currentWaitMillis < maxWaitMillis) {
			try {
				TimeUnit.MILLISECONDS.sleep(tryTimeInterval);
			} catch (InterruptedException e) {
			}
			currentWaitMillis += tryTimeInterval;
			lock = getLock(lockKey);
		}
		return lock;
	}

	/**
	 * 从Redis中获取锁，获取成功返厍true，获取失败返回false。<br>
	 * 注：需要手动执行delLock删除锁定Key，否则会一直保留
	 * 
	 * @param lockKey
	 * @return
	 */
	public static boolean getLock(String lockKey) {
		try {
			lockKey = "lockKey-" + lockKey;
			boolean con = false;
			do {
				long now = System.currentTimeMillis();
				// Redis的GetSet返回的值必须是字符串，否则会抛异常，因而将其转换为字符串
				String nowTime = String.valueOf(now);
				con = false;
				// 返回1表示锁获取成功，返回0表示锁取失败
				Long result = RedisUtil.getRedisUtil().setnx(getDefineKey(lockKey), nowTime);
				if (1 == result) {
					return true;
				} else {
					Object lockKeyValue = redisTemplate.opsForValue().get(getDefineKey(lockKey));
					if (null != lockKeyValue) {
						String oldTime = String.valueOf(lockKeyValue);
						// 检查锁lockKey的值是不是超过了设定的时间，如2秒钟，如果超过了则继续尝试获取锁，
						// 直到获取到锁，或者数据未超期时退出，循环判断可以解决死锁的问题
						if (now - Long.parseLong(oldTime) >= lockExpireTime) {// 数据已经过期了
							// 超期将其从缓存中删除，避免后续的请求获取不了锁的情况
							del(lockKey);
							con = true;
						}
					}
				}
			} while (con);
		} catch (Throwable t) {
			log.error("从Redis中获取key为" + getDefineKey(lockKey) + "的锁失败:" + t.getMessage(), t);
			// throw new RuntimeException(t);
		}
		return false;
	}

	/**
	 * 删除Lock Key代表的内容
	 * 
	 * @param lockKey
	 */
	public static void delLock(String lockKey) {
		lockKey = "lockKey-" + lockKey;
		redisTemplate.delete(getDefineKey(lockKey));
	}

	public static void del(String key) {
		redisTemplate.delete(getDefineKey(key));
	}

	public static void del(Collection<String> keys) {
		List<String> sKeys = new ArrayList<String>();
		keys.forEach(k -> {
			sKeys.add(getDefineKey(k));
		});
		redisTemplate.delete(sKeys);
	}

	/**
	 * 将值写入到redis的zset中
	 * 
	 * @param key
	 * @param value
	 */
	public static void zSetAdd(String key, Object value, double score) {
		redisTemplate.opsForZSet().add(getDefineKey(key), value, score);
	}

	public static void zSetDel(String key, Object... values) {
		redisTemplate.opsForZSet().remove(getDefineKey(key), values);
	}

	/**
	 * 从zSet获取值
	 * 
	 * @param key
	 * @param page
	 * @param size
	 * @return
	 */
	public static Set<TypedTuple<Object>> getZSet(String key, int page, int size) {
		Set<TypedTuple<Object>> set = null;
		if (redisTemplate != null) {
			page = page < 1 ? 1 : page;
			size = size < 1 ? 1 : size;
			int start = (page - 1) * size;
			int end = start + size - 1;
			set = redisTemplate.opsForZSet().reverseRangeWithScores(getDefineKey(key), start, end);
		}
		return set;
	}

	/**
	 * 将值写入到redis的hashset中
	 * 
	 * @param key
	 * @param value
	 */
	public static void hSet(String key, String hashKey, Object value) {
		redisTemplate.opsForHash().put(getDefineKey(key), hashKey, value);
	}

	/**
	 * 从hashset中获取指定hashKey数据
	 * 
	 * @param key
	 * @param hashKey
	 */
	public static Object hGet(String key, Object hashKey) {
		List<Object> hashKeyList = new ArrayList<Object>();
		hashKeyList.add(hashKey);
		List<Object> list = hGets(key, hashKeyList);
		if (!CollectionUtils.isEmpty(list)) {
			return list.get(0);
		}
		return null;
	}

	/**
	 * 从hashset中批量获取数据
	 * 
	 * @param key
	 * @param hashKeys
	 */
	public static List<Object> hGets(String key, Collection<Object> hashKeys) {
		List<Object> result = new ArrayList<Object>();

		List<Object> list = redisTemplate.opsForHash().multiGet(getDefineKey(key), hashKeys);
		if (list != null) {
			result.addAll(list);
		}

		return result;
	}

	/**
	 * 获取当前key对应的所有hashkey的列表
	 * 
	 * @param key
	 * @return
	 */
	public static List<Object> hGetHashKeys(String key) {
		List<Object> result = new ArrayList<Object>();

		Set<Object> set = redisTemplate.opsForHash().keys(getDefineKey(key));
		if (set != null) {
			result.addAll(set);
		}

		return result;
	}

	public static void hDel(String key, Object... hashKeys) {
		redisTemplate.opsForHash().delete(getDefineKey(key), hashKeys);
	}

	private static String getDefineKey(String key) {
		return key;
	}

	/*********************
	 * 以下方法用于支持旧的RedisStarter:com.eeeffff.redis-eeeffff-starter
	 ********************/
	public static void del(BaseCacheKey baseCacheKey, Object... param) {
		redisTemplate.opsForValue().getOperations().delete(baseCacheKey.getKey(param));
	}

	public static void del(Collection<BaseCacheKey> keys, Object... param) {
		Iterator<BaseCacheKey> it = keys.iterator();
		List<String> list = new ArrayList<>();
		while (it.hasNext()) {
			BaseCacheKey baseCacheKey = it.next();
			list.add(baseCacheKey.getKey(param));
		}
		redisTemplate.opsForValue().getOperations().delete(list);
	}

	public static boolean expire(long timeout, TimeUnit unit, BaseCacheKey baseCacheKey, Object... param) {
		if (timeout < 0L) {
			return false;
		}
		return redisTemplate.opsForValue().getOperations().expire(baseCacheKey.getKey(param), timeout, unit)
				.booleanValue();
	}

	public static boolean expire(BaseCacheKey baseCacheKey, Object... param) {
		return expire(baseCacheKey.getExpirationTime(), baseCacheKey.getExpirationTimeUnit(), baseCacheKey, param);
	}

	public static boolean expire(BaseCacheKey baseCacheKey, LocalDateTime expireDate, Object... param) {
		long timeout = DateTimeUtils.periodSecond(expireDate, LocalDateTime.now()).longValue();
		return expire(baseCacheKey, new Object[] { Long.valueOf(timeout), TimeUnit.SECONDS, param });
	}

	public static long ttl(BaseCacheKey baseCacheKey, Object... param) {
		return ttl(baseCacheKey, TimeUnit.SECONDS, param);
	}

	public static long ttl(BaseCacheKey baseCacheKey, TimeUnit unit, Object... param) {
		return redisTemplate.opsForValue().getOperations().getExpire(baseCacheKey.getKey(param), unit).longValue();
	}

	public static boolean exist(BaseCacheKey baseCacheKey, Object... param) {
		return redisTemplate.opsForValue().getOperations().hasKey(baseCacheKey.getKey(param)).booleanValue();
	}

	public static void set(BaseCacheKey baseCacheKey, Object value, Object... param) {
		if (baseCacheKey.getExpirationTime() > -1) {
			redisTemplate.opsForValue().set(baseCacheKey.getKey(param), value, baseCacheKey.getExpirationTime(),
					baseCacheKey.getExpirationTimeUnit());
		} else {
			redisTemplate.opsForValue().set(baseCacheKey.getKey(param), value);
		}
	}

}
