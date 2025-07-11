package com.hmdp;

import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
class HmDianPingApplicationTests {


    @Resource
    private IShopService shopService; // 改为接口类
    @Test
    void testSaveShop() throws InterruptedException {
        shopService.saveShopToCache(1L, 10L);
    }
}
