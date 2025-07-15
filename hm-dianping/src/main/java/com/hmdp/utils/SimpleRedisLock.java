package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    /**
     * RedisTemplate
     */
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 锁的名称
     */
    private String name;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }


    /**
     * 尝试获取锁
     *
     * @param timeoutSec 锁的过期时间
     * @return
     */
    @Override
    public boolean tryLock(long timeoutSec) {
        // 获取锁
     String id = Thread.currentThread().getId() + "";
     Boolean ressult = stringRedisTemplate.opsForValue()
             .setIfAbsent("lock:"+name, id, timeoutSec, TimeUnit.MINUTES);
     return Boolean.TRUE.equals(ressult);
    }

    /**
     * 释放锁
     */
    @Override
    public void unLock() {
        // 获取线程id
        String id = Thread.currentThread().getId() + "";
        // 获取锁中的id
        String id1 = stringRedisTemplate.opsForValue().get("lock:"+name);
        // 判断锁中的id和当前线程的id是否一致
        if (id.equals(id1)){
            // 释放锁
            stringRedisTemplate.delete("lock:"+name);
        }
    }
}
