package com.hmdp.utils;


import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.core.io.ClassPathResource;
import java.util.Collections;
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

    /**
     * key前缀
     */
    public static final String KEY_PREFIX = "lock:";

    public static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

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
        String threadId = ID_PREFIX + Thread.currentThread().getId() + "";
        // SET lock:name id EX timeoutSec NX
        Boolean result = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(result);
    }


    /**
     * 加载Lua脚本
     */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    /**
     * 释放锁
     */

    @Override
    public  void unLock(){
        // 执行lua脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId()
        );
    }
    /*@Override
    public void unLock() {
        // 判断 锁的线程标识 是否与 当前线程一致
        String currentThreadFlag = ID_PREFIX + Thread.currentThread().getId();
        String redisThreadFlag = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        if (currentThreadFlag != null || currentThreadFlag.equals(redisThreadFlag)) {
            // 一致，说明当前的锁就是当前线程的锁，可以直接释放
            stringRedisTemplate.delete(KEY_PREFIX + name);
       }
       // 不一致,则不能释放
    }*/
}
