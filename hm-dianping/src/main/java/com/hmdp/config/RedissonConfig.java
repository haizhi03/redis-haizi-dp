package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient() {
        // 获取Redisson配置对象
        Config config = new Config();
        // 添加redis地址, 这里添加的是单节点地址, 也可以通过 config.useClusterServers() 添加集群地址
        config.useSingleServer().setAddress("redis://192.168.238.133:6379").setPassword("123321");
        // 获取Redisson对象,并交给ioc进行管理
        return Redisson.create(config);
    }
}
