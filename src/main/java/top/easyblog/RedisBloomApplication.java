package top.easyblog;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author ：huangxin
 * @modified ：
 * @since ：2020/06/10 16:55
 */
@SpringBootApplication
@MapperScan(value = "top.easyblog.mapper")
public class RedisBloomApplication {
    public static void main(String[] args) {
        SpringApplication.run(RedisBloomApplication.class,args);
    }
}
