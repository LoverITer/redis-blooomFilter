package top.easyblog;

import com.google.common.base.Charsets;
import com.google.common.hash.Funnel;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import top.easyblog.common.BloomFilterHelper;
import top.easyblog.common.enums.RedisDBSelector;
import top.easyblog.common.util.RedisUtils;
import top.easyblog.entity.User;
import top.easyblog.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import top.easyblog.service.UserService;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author ：huangxin
 * @modified ：
 * @since ：2020/06/10 20:03
 */

@RunWith(SpringRunner.class)
@SpringBootTest
public class AppTest {


    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private UserService userService;
    @Test
    public void insert(){
        int count=0;
        for(int i=1;i<=1000;i++){
            List<User> users=new ArrayList<>(1000);
            for(int j=0;j<1000;j++){
                User user = new User();
                user.setAge(new Random(10).nextInt(70));
                user.setGender(j%2==0?"M":"F");
                user.setName(UUID.randomUUID().toString());
                users.add(user);
            }
            userMapper.insertBatch(users);
            System.out.println("第"+i+"次批量插入成功...");
        }
    }

    @Before
    public void before(){
        //测试之前先将数据加载到RedisBloom中
        BloomFilterHelper<String> filter = new BloomFilterHelper<>((Funnel<String>) (from, into) ->
                into.putString(from, Charsets.UTF_8).putString(from, Charsets.UTF_8), 1500000, 0.001);
        List<User> users = userMapper.selectAll();
        Objects.requireNonNull(users).forEach(user->{
            redisUtils.add2BloomFilter(filter,"USER_INFO",user.getUserId()+"", RedisDBSelector.DB_1);
        });

    }

    @Test
    public void test(){
        ThreadPoolExecutor executor=new ThreadPoolExecutor(1000,2000,60, TimeUnit.SECONDS,new LinkedBlockingQueue<>(),new ThreadPoolExecutor.CallerRunsPolicy());
        for(int i=0;i<2000;i++) {
            int finalI = i;
            executor.submit(() -> {
                userService.getUserById(finalI);
            });
        }
    }

}
