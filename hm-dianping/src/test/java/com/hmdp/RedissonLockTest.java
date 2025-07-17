package com.hmdp;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
@Slf4j
public class RedissonLockTest {

    @Resource
    private RedissonClient redissonClient;
    private RLock Lock;

    /**
     * 方法1获取锁一次
     *
     */
    @Test
    void testLock1() {
        boolean isLock = false;
        // 创建锁对象
        Lock = redissonClient.getLock("lock");
        try {
            isLock = Lock.tryLock();
            if (!isLock) {
                log.error("获取锁失败,1");
                return;
            }
            log.info("获取锁成功,1");
            testLock2();
        }finally {
            if (isLock){
                log.info("释放锁，1");
                Lock.unlock();
            }
        }
    }
    /**
     * 方法二再获取一次锁
     */
    void testLock2() {
        boolean isLock = false;
        try {
            isLock = Lock.tryLock();
            if (!isLock) {
                log.error("获取锁失败, 2");
                return;
            }
            log.info("获取锁成功，2");
        } finally {
            if (isLock) {
                log.info("释放锁，2");
                Lock.unlock();
            }
        }
    }
}
