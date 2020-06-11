package top.easyblog.common.util;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.*;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import top.easyblog.ApplicationContextHolder;
import top.easyblog.common.BloomFilterHelper;
import top.easyblog.common.enums.RedisDBSelector;
import top.easyblog.redis.ObjectRedisTemplate;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;


/**
 * Redis工具类，封装了对象和Redis基本数据类型的大部分存,取,删除,设置过期时间等操作. 所有操作可以指定数据库索引.
 * 存,取可以设置过期时间. 没有设置默认过期时间,存值时尽量设置过期时间
 *
 * @author ：huangxin
 * @modified ：
 * @since ：2020/02/27 11:56
 */
@Slf4j
@Component(value = "redisUtils")
public class RedisUtils {

    @Autowired
    private ObjectRedisTemplate redisTemplate;
    @Autowired
    private ValueOperations<String, Object> redisValueOps;
    @Autowired
    private HashOperations<String, String, Object> redisHashOps;
    @Autowired
    private ListOperations<String, Object> redisListOps;
    @Autowired
    private SetOperations<String, Object> redisSetOps;
    @Autowired
    private ZSetOperations<String, Object> redisZSetOps;


    /**
     * Redis数据库最大索引
     */
    private static final int MAX_DB_INDEX = 15;
    /**
     * Redis数据库最小索引
     */
    private static final int MIN_DB_INDEX = 0;
    /**
     * 默认过期时间
     */
    private static final int DEFAULT_EXPIRE = 60 * 60 * 24;
    /**
     * 不过期
     */
    private static final int NON_EXPIRE = -1;

    /**
     * redis读写工具类
     */
    private static RedisUtils redisUtils = null;

    public static RedisUtils getRedisUtils() {
        if (Objects.isNull(redisUtils)) {
            synchronized (RedisUtils.class) {
                redisUtils = ApplicationContextHolder.getBean("redisUtils");
            }
        }
        return redisUtils;
    }


    //=============================common============================//

    /**
     * 向外暴露RedisTemplate
     *
     * @param dbIndex
     * @return
     */
    public RedisTemplate getRedisTemplate(RedisDBSelector dbIndex) {
        setDbIndex(dbIndex);
        return redisTemplate;
    }

    /**
     * 设置数据库索引
     *
     * @param dbIndex
     */
    private void setDbIndex(RedisDBSelector dbIndex) {
        if (dbIndex == null || dbIndex.getDb() > MAX_DB_INDEX || dbIndex.getDb() < MIN_DB_INDEX) {
            dbIndex = RedisDBSelector.DB_0;
        }
        LettuceConnectionFactory jedisConnectionFactory = (LettuceConnectionFactory) redisTemplate.getConnectionFactory();
        assert jedisConnectionFactory != null;
        jedisConnectionFactory.setDatabase(dbIndex.getDb());
        redisTemplate.setConnectionFactory(jedisConnectionFactory);
    }

    /**
     * 指定缓存失效时间
     *
     * @param key     键
     * @param time    时间(秒)
     * @param dbIndex 读写操作的库
     * @return
     */
    public Boolean expire(String key, long time, RedisDBSelector dbIndex) {
        try {
            this.setDbIndex(dbIndex);
            if (time > 0) {
                return redisTemplate.expire(key, time, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 根据key 获取过期时间
     *
     * @param key 键 不能为null
     * @return 时间(秒) 返回0代表为永久有效
     */
    public Long getExpire(String key, RedisDBSelector dbIndex) {
        try {
            this.setDbIndex(dbIndex);
            return redisTemplate.getExpire(key, TimeUnit.SECONDS);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0L;
    }

    /**
     * 判断key是否存在
     *
     * @param key 键
     * @return true 存在 false不存在
     */
    public Boolean hasKey(String key, RedisDBSelector dbIndex) {
        try {
            this.setDbIndex(dbIndex);
            return redisTemplate.hasKey(key);
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return false;
    }

    /**
     * 删除缓存
     *
     * @param key 可以传一个值 或多个
     */
    @SuppressWarnings("unchecked")
    public Boolean delete(RedisDBSelector dbIndex, String... key) {
        if (key != null && key.length > 0) {
            this.setDbIndex(dbIndex);
            if (key.length == 1) {
                return redisTemplate.delete(key[0]);
            } else {
                Long deleted = redisTemplate.delete(CollectionUtils.arrayToList(key));
                if (Objects.nonNull(deleted) && deleted > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    public void renameKey(String oldKey, String newKey, RedisDBSelector dbIndex) {
        this.setDbIndex(dbIndex);
        redisTemplate.rename(oldKey, newKey);
    }

    //============================String=============================//

    /**
     * 普通缓存获取
     *
     * @param key 键
     * @return 值
     */
    public Object get(String key, RedisDBSelector dbIndex) {
        this.setDbIndex(dbIndex);
        return key == null ? null : redisValueOps.get(key);
    }

    /**
     * 普通缓存放入
     *
     * @param key   键
     * @param value 值
     * @return true成功 false失败
     */
    public boolean set(String key, Object value, RedisDBSelector dbIndex) {
        try {
            this.setDbIndex(dbIndex);
            redisValueOps.set(key, value);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

    }

    /**
     * 普通缓存放入并设置时间
     *
     * @param key   键
     * @param value 值
     * @param time  时间(秒) time要大于0 如果time小于等于0 将设置无限期
     * @return true成功 false 失败
     */
    public boolean set(String key, Object value, long time, RedisDBSelector dbIndex) {
        try {
            this.setDbIndex(dbIndex);
            if (time > 0) {
                redisValueOps.set(key, value, time, TimeUnit.SECONDS);
            } else {
                redisValueOps.set(key, value);
            }
            return true;
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return false;
    }

    /**
     * 如果key在redis中不存在，那就添加，否者不添加
     *
     * @param key     关键字
     * @param value   值
     * @param time    过期时间
     * @param dbIndex 选择redis的数据库
     * @return
     */
    public Boolean setNX(String key, Object value, long time, RedisDBSelector dbIndex) {
        try {
            this.setDbIndex(dbIndex);
            if (time > 0) {
                return redisValueOps.setIfAbsent(key, value, time, TimeUnit.SECONDS);
            } else {
                return redisValueOps.setIfAbsent(key, value);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 如何Key在redis中存在，则添加到Redis中，否者不添加
     *
     * @param key
     * @param value
     * @param time
     * @param dbIndex
     * @return
     */
    public Boolean setXX(String key, Object value, long time, RedisDBSelector dbIndex) {
        try {
            this.setDbIndex(dbIndex);
            if (time > 0) {
                return redisValueOps.setIfPresent(key, value, time, TimeUnit.SECONDS);
            } else {
                return redisValueOps.setIfPresent(key, value);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 递增
     *
     * @param key   键
     * @param delta 要增加几(大于0)
     * @return
     */
    public Long incr(String key, long delta) {
        if (delta < 0) {
            throw new RuntimeException("递增因子必须大于0");
        }
        return redisValueOps.increment(key, delta);
    }

    /**
     * 递减
     *
     * @param key   键
     * @param delta 要减少几(小于0)
     * @return
     */
    public Long decr(String key, long delta) {
        if (delta < 0) {
            throw new RuntimeException("递减因子必须大于0");
        }
        return redisValueOps.increment(key, -delta);
    }

    //================================Map=================================//

    /**
     * HashGet
     *
     * @param key  键 不能为null
     * @param item 项 不能为null
     * @return 值
     */
    public Object hget(String key, String item, RedisDBSelector dbIndex) {
        this.setDbIndex(dbIndex);
        return redisHashOps.get(key, item);
    }

    /**
     * 获取hashKey对应的所有键值
     *
     * @param key 键
     * @return 对应的多个键值
     */
    public Map<String, Object> hmget(String key, RedisDBSelector dbIndex) {
        this.setDbIndex(dbIndex);
        return redisHashOps.entries(key);
    }

    /**
     * HashSet
     *
     * @param key 键
     * @param map 对应多个键值
     * @return true 成功 false 失败
     */
    public boolean hmset(String key, Map<String, Object> map, RedisDBSelector dbIndex) {
        try {
            this.setDbIndex(dbIndex);
            redisHashOps.putAll(key, map);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * HashSet 并设置时间
     *
     * @param key  键
     * @param map  对应多个键值
     * @param time 时间(秒)
     * @return true成功 false失败
     */
    public boolean hmset(String key, Map<String, Object> map, long time, RedisDBSelector dbIndex) {
        try {
            this.setDbIndex(dbIndex);
            redisHashOps.putAll(key, map);
            if (time > 0) {
                expire(key, time, dbIndex);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 向一张hash表中放入数据,如果不存在将创建
     *
     * @param key   键
     * @param item  项
     * @param value 值
     * @return true 成功 false失败
     */
    public boolean hset(String key, String item, Object value, RedisDBSelector dbIndex) {
        try {
            this.setDbIndex(dbIndex);
            redisHashOps.put(key, item, value);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 向一张hash表中放入数据,如果不存在将创建
     *
     * @param key   键
     * @param item  项
     * @param value 值
     * @param time  时间(秒)  注意:如果已存在的hash表有时间,这里将会替换原有的时间
     * @return true 成功 false失败
     */
    public boolean hset(String key, String item, Object value, long time, RedisDBSelector dbIndex) {
        try {
            this.setDbIndex(dbIndex);
            redisHashOps.put(key, item, value);
            if (time > 0) {
                expire(key, time, dbIndex);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 删除hash表中的值
     *
     * @param key  键 不能为null
     * @param item 项 可以使多个 不能为null
     */
    public void hdel(RedisDBSelector dbIndex, String key, Object... item) {
        this.setDbIndex(dbIndex);
        redisHashOps.delete(key, item);
    }

    /**
     * 判断hash表中是否有该项的值
     *
     * @param key  键 不能为null
     * @param item 项 不能为null
     * @return true 存在 false不存在
     */
    public boolean hHasKey(String key, String item, RedisDBSelector dbIndex) {
        this.setDbIndex(dbIndex);
        return redisHashOps.hasKey(key, item);
    }

    /**
     * hash递增 如果不存在,就会创建一个 并把新增后的值返回
     *
     * @param key   键
     * @param item  项
     * @param delta 要增加几(大于0)
     * @return
     */
    public double hincr(String key, String item, double delta, RedisDBSelector dbIndex) {
        this.setDbIndex(dbIndex);
        return redisHashOps.increment(key, item, delta);
    }

    /**
     * hash递减
     *
     * @param key  键
     * @param item 项
     * @param by   要减少记(小于0)
     * @return
     */
    public double hdecr(String key, String item, double by, RedisDBSelector dbIndex) {
        this.setDbIndex(dbIndex);
        return redisHashOps.increment(key, item, -by);
    }

    //============================Set=================================//

    /**
     * 根据key获取Set中的所有值
     *
     * @param key 键
     * @return
     */
    public Set<Object> sGet(String key, RedisDBSelector dbIndex) {
        try {
            this.setDbIndex(dbIndex);
            return redisTemplate.opsForSet().members(key);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 根据value从一个set中查询,是否存在
     *
     * @param key   键
     * @param value 值
     * @return true 存在 false不存在
     */
    public Boolean sHasKey(String key, Object value, RedisDBSelector dbIndex) {
        try {
            this.setDbIndex(dbIndex);
            return redisTemplate.opsForSet().isMember(key, value);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 将数据放入set缓存
     *
     * @param key    键
     * @param values 值 可以是多个
     * @return 成功个数
     */
    public Long sSet(String key, RedisDBSelector dbIndex, Object... values) {
        try {
            this.setDbIndex(dbIndex);
            return redisTemplate.opsForSet().add(key, values);
        } catch (Exception e) {
            e.printStackTrace();
            return 0L;
        }
    }

    /**
     * 将set数据放入缓存
     *
     * @param key    键
     * @param time   时间(秒)
     * @param values 值 可以是多个
     * @return 成功个数
     */
    public Long sSetAndTime(String key, long time, RedisDBSelector dbIndex, Object... values) {
        try {
            this.setDbIndex(dbIndex);
            Long count = redisTemplate.opsForSet().add(key, values);
            if (time > 0) {
                expire(key, time, dbIndex);
            }
            return count;
        } catch (Exception e) {
            e.printStackTrace();
            return 0L;
        }
    }

    /**
     * 获取set缓存的长度
     *
     * @param key 键
     * @return
     */
    public Long sGetSetSize(String key, RedisDBSelector dbIndex) {
        try {
            this.setDbIndex(dbIndex);
            return redisTemplate.opsForSet().size(key);
        } catch (Exception e) {
            e.printStackTrace();
            return 0L;
        }
    }

    /**
     * 移除值为value的
     *
     * @param key    键
     * @param values 值 可以是多个
     * @return 移除的个数
     */
    public Long setRemove(String key, RedisDBSelector dbIndex, Object... values) {
        try {
            this.setDbIndex(dbIndex);
            return redisTemplate.opsForSet().remove(key, values);
        } catch (Exception e) {
            e.printStackTrace();
            return 0L;
        }
    }
    //===============================list=================================//

    /**
     * 获取list缓存的内容
     *
     * @param key   键
     * @param start 开始
     * @param end   结束  0 到 -1代表所有值
     * @return
     */
    public List<Object> lGet(String key, long start, long end, RedisDBSelector dbIndex) {
        try {
            this.setDbIndex(dbIndex);
            return redisListOps.range(key, start, end);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 获取list缓存的长度
     *
     * @param key 键
     * @return
     */
    public Long lGetListSize(String key, RedisDBSelector dbIndex) {
        try {
            this.setDbIndex(dbIndex);
            return redisListOps.size(key);
        } catch (Exception e) {
            e.printStackTrace();
            return 0L;
        }
    }

    /**
     * 通过索引 获取list中的值
     *
     * @param key   键
     * @param index 索引  index>=0时， 0 表头，1 第二个元素，依次类推；index<0时，-1，表尾，-2倒数第二个元素，依次类推
     * @return
     */
    public Object lGetIndex(String key, long index, RedisDBSelector dbIndex) {
        try {
            this.setDbIndex(dbIndex);
            return redisListOps.index(key, index);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 将list放入缓存
     *
     * @param key   键
     * @param value 值
     * @return
     */
    public boolean lSet(String key, Object value, RedisDBSelector dbIndex) {
        try {
            this.setDbIndex(dbIndex);
            redisListOps.rightPush(key, value);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 将list放入缓存
     *
     * @param key   键
     * @param value 值
     * @param time  时间(秒)
     * @return
     */
    public boolean lSet(String key, Object value, long time, RedisDBSelector dbIndex) {
        try {
            redisListOps.rightPush(key, value);
            if (time > 0) {
                expire(key, time, dbIndex);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 将list放入缓存
     *
     * @param key   键
     * @param value 值
     * @return
     */
    public boolean lSet(String key, List<Object> value, RedisDBSelector dbIndex) {
        try {
            this.setDbIndex(dbIndex);
            redisListOps.rightPushAll(key, value);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 将list放入缓存
     *
     * @param key   键
     * @param value 值
     * @param time  时间(秒)
     * @return
     */
    public boolean lSet(String key, List<Object> value, long time, RedisDBSelector dbIndex) {
        try {
            this.setDbIndex(dbIndex);
            redisListOps.rightPushAll(key, value);
            if (time > 0) {
                expire(key, time, dbIndex);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 根据索引修改list中的某条数据
     *
     * @param key   键
     * @param index 索引
     * @param value 值
     * @return
     */
    public boolean lUpdateIndex(String key, long index, Object value, RedisDBSelector dbIndex) {
        try {
            this.setDbIndex(dbIndex);
            redisListOps.set(key, index, value);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 移除N个值为value
     *
     * @param key   键
     * @param count 移除多少个
     * @param value 值
     * @return 移除的个数
     */
    public Long lRemove(String key, long count, Object value, RedisDBSelector dbIndex) {
        try {
            this.setDbIndex(dbIndex);
            return redisListOps.remove(key, count, value);
        } catch (Exception e) {
            e.printStackTrace();
            return 0L;
        }
    }


    //===========================redis bloom filter==============//

    /**
     * 向Redis的布隆过滤器中添加元素
     *
     * @param helper BloomFilterHelper<T>
     * @param key    布隆过滤器名
     * @param value  值
     * @param <T>    参数的值
     * @return 添加成功返回true, 添加失败返回false
     */
    public <T> Boolean add2BloomFilter(BloomFilterHelper<T> helper, String key, T value, RedisDBSelector dbIndex) {
        try {
            this.setDbIndex(dbIndex);
            int[] offset = helper.murmurHashOffset(value);
            for (int i : offset) {
                redisValueOps.setBit(key, i, true);
            }
            return true;
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return false;
    }

    /**
     * 判断元素在RedisBloom中是否存在
     *
     * @param helper  BloomFilterHelper<T>
     * @param key     布隆过滤器名
     * @param value   值
     * @param dbIndex 选择Redis数据库
     * @param <T>     参数的值
     * @return 可能存在返回true，一定不存在false
     */
    public <T> Boolean mightContain(BloomFilterHelper<T> helper, String key, T value, RedisDBSelector dbIndex) {
        try {
            this.setDbIndex(dbIndex);
            int[] offset = helper.murmurHashOffset(value);
            for (int i : offset) {
                if (!redisValueOps.getBit(key, i)) {
                    return false;
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return true;
    }

}