**封装的redis-spring-boot-starter**

集成Redis操作的，并提供方便用户操作com.eeeffff.redis.spring.boot.RedisTemplateWrapper包装类，包括可以轻松的实现锁，如下代码所示：

```java
String lockKey = "...";
try {
  //获取操作锁，如果获取锁失败，每次等待100毫秒后再试，最多尝试50次，该锁不会造成死锁，会在一定的时间后自动判断当时是否处理于死锁状态
  //如果判断为死锁状态中，则自动解锁
  if(!RedisTemplateWrapper.getLock(lockKey,5000,100)) {
        return ;
  }
  continueDoTask();
} catch (Exception e) {
  throw new RuntimeException("获取操作锁失败，请稍后再试");
}finally {
  // 删除锁
  RedisTemplateWrapper.delLock(lockKey);
}
```

Maven pom.xml集成：

```xml
<dependency>
  <groupId>com.eeeffff</groupId>
  <artifactId>redis-spring-boot-starter</artifactId>
  <version>1.1.10</version>
</dependency>
```

