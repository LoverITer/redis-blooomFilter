package top.easyblog.service;

import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Charsets;
import com.google.common.hash.Funnel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.easyblog.common.BloomFilterHelper;
import top.easyblog.common.enums.RedisDBSelector;
import top.easyblog.common.util.RedisUtils;
import top.easyblog.entity.User;
import top.easyblog.mapper.UserMapper;

/**
 * @author ：huangxin
 * @modified ：
 * @since ：2020/06/10 19:21
 */
@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private RedisUtils redisUtils;


    /**
     * 根据Id获取用户数据
     *
     * @param id
     * @return
     */
    public User getUserById(int id) {
        //布隆过滤器先过滤是否是合适的id
        BloomFilterHelper<String> filter = new BloomFilterHelper<>((Funnel<String>) (from, into) ->
                into.putString(from, Charsets.UTF_8).putString(from, Charsets.UTF_8), 1500000, 0.001);
        Boolean isOk = redisUtils.mightContain(filter, "USER_INFO", id + "", RedisDBSelector.DB_1);
        if(isOk) {
            System.out.println("id: "+id+"可能存在，去Redis中查缓存");
            //验证过了之后去Redis中尝试获取
            String infoStr = (String) redisUtils.hget("USER_INFO", id + "", RedisDBSelector.DB_0);
            User user = JSONObject.parseObject(infoStr, User.class);
            if (user == null) {
                //发现没有再从数据库中查询
                user = userMapper.selectByPrimaryKey(id);
                redisUtils.hset("USER_INFO", id + "", JSONObject.toJSONString(user), 60 * 60, RedisDBSelector.DB_0);
            }
            return user;
        }
        System.out.println("id: "+id+"不存在，去Redis中查缓存");
        return null;
    }

}
