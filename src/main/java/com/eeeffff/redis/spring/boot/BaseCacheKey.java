package com.eeeffff.redis.spring.boot;

import java.util.concurrent.TimeUnit;
/**
 * @author fenglibin
 *
 */
public interface BaseCacheKey {
  public static final String CACHE_KEY_SEPARATOR = ":";
  
  String setPrefix();
  
  String getKey(Object... paramVarArgs);
  
  int getExpirationTime();
  
  TimeUnit getExpirationTimeUnit();
}
