package com.cmic.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
public class RedisUtil {
    @Autowired
    private RedisTemplate redisTemplate;

    public Set<String> keys(String prefixKey){
        return redisTemplate.keys(prefixKey);
    }


    /**
     * 普通缓存放入并设置时间
     * @param key 键
     * @param value 值
     * @param time 时间(秒) time要大于0 如果time小于等于0 将设置无限期
     * @return
     */
    public void set(String key, Object value, long time){
        redisTemplate.opsForValue().set(key, value, time, TimeUnit.SECONDS);
    }

    /**
     * 普通缓存放入并设置时间
     * @param key
     * @param value
     * @param time
     * @param unit
     * @return
     */
    public void set(String key, Object value, long time, TimeUnit unit){
        redisTemplate.opsForValue().set(key, value, time, unit);
    }

    /**
     * 普通缓存放入
     * @param key 键
     * @param value 值
     * @return
     */
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * 判断key是否存在
     * @param key 键
     * @return true 存在 false不存在
     */
    public boolean hasKey(String key){
        return redisTemplate.hasKey(key);
    }

    /**
     * 删除key
     * @param key 键
     * @return true成功 false失败
     */
    public boolean delKey(String key){
        return redisTemplate.delete(key);
    }

    public void delKeys(Set<String> keys){
        redisTemplate.delete(keys);
    }

    public Object get(String key){
        return redisTemplate.opsForValue().get(key);
    }


    //================================Map=================================

    public Long hSize(String key) {
        return redisTemplate.opsForHash().size(key);
    }

    /**
     * HashGet
     * @param key 键 不能为null
     * @param item 项 不能为null
     * @return 值
     */
    public Object hget(String key, String item){
        return redisTemplate.opsForHash().get(key, item);
    }

    /**
     * 获取hashKey对应的所有键值
     * @param key 键
     * @return 对应的多个键值
     */
    public Map<String,Object> hmget(String key){
        return redisTemplate.opsForHash().entries(key);
    }

    /**
     * HashSet
     * @param key 键
     * @param map 对应多个键值
     * @return
     */
    public void hmset(String key, Map<String,Object> map){
        redisTemplate.opsForHash().putAll(key, map);
    }

    /**
     * HashSet 并设置时间
     * @param key 键
     * @param map 对应多个键值
     * @param time 时间(秒)
     * @return true成功 false失败
     */
    public boolean hmset(String key, Map<String,Object> map, long time, TimeUnit timeUnit){
        redisTemplate.opsForHash().putAll(key, map);
        return expire(key, time, timeUnit);
    }

    /**
     * 向一张hash表中放入数据,如果不存在将创建
     * @param key 键
     * @param item 项
     * @param value 值
     * @return true 成功 false失败
     */
    public void hset(String key, String item, Object value) {
        redisTemplate.opsForHash().put(key, item, value);
    }

    /**
     * 向一张hash表中放入数据,如果不存在将创建
     * @param key 键
     * @param item 项
     * @param value 值
     * @param time 时间  注意:如果已存在的hash表有时间,这里将会替换原有的时间
     * @param timeUnit 单位
     * @return true 成功 false失败
     */
    public boolean hset(String key, String item, Object value, long time, TimeUnit timeUnit) {
        redisTemplate.opsForHash().put(key, item, value);
        return expire(key, time,timeUnit);
    }

    /**
     * 失效时间
     * @param key
     * @param time
     * @param timeUnit
     * @return
     */
    public boolean expire(String key, long time, TimeUnit timeUnit){
       return redisTemplate.expire(key, time, timeUnit);
    }


    /**
     * 删除hash表中的值
     * @param key 键 不能为null
     * @param item 项 可以使多个 不能为null
     */
    public void hdel(String key, Object... item){
        redisTemplate.opsForHash().delete(key,item);
    }

    /**
     * 判断hash表中是否有该项的值
     * @param key 键 不能为null
     * @param item 项 不能为null
     * @return true 存在 false不存在
     */
    public boolean hHasKey(String key, String item){
        return redisTemplate.opsForHash().hasKey(key, item);
    }

    /**
     * hash递增 如果不存在,就会创建一个 并把新增后的值返回
     * @param key 键
     * @param item 项
     * @param by 要增加几(大于0)
     * @return
     */
    public double hincr(String key, String item, double by){
        return redisTemplate.opsForHash().increment(key, item, by);
    }

    /**
     * hash递减
     * @param key 键
     * @param item 项
     * @param by 要减少记(小于0)
     * @return
     */
    public double hdecr(String key, String item, double by){
        return redisTemplate.opsForHash().increment(key, item,-by);
    }

    /**
     * list right push
     * @param key 键
     * @param value 值
     * @return true成功 false失败
     */
    public Long lpush(String key, Object value) {
        return redisTemplate.opsForList().rightPush(key, value);
    }

    public List lget(String key){
        return redisTemplate.opsForList().range(key, 0, -1);
    }


    /*****************操作 set 集合***********************************************/

    // 向集合中添加一个或多个成员
    public Long addToSet(String key, Object... values) {
        return redisTemplate.opsForSet().add(key, values);
    }

    // 返回集合中的所有成员
    public Set<Object> getSet(String key) {
        return redisTemplate.opsForSet().members(key);
    }

    // 判断集合中是否存在指定成员
    public Boolean isMember(String key, Object value) {
        return redisTemplate.opsForSet().isMember(key, value);
    }

    // 从集合中移除一个或多个成员
    public Long removeFromSet(String key, Object... values) {
        return redisTemplate.opsForSet().remove(key, values);
    }

    // 获取集合的大小
    public Long getSizeOfSet(String key) {
        return redisTemplate.opsForSet().size(key);
    }

    // 从一个集合移动成员到另一个集合
    public Boolean moveMember(String srcKey, String destKey, Object member) {
        return redisTemplate.opsForSet().move(srcKey, member, destKey);
    }

    // 获取两个集合的交集
    public Set<String> intersectSets(String key1, String key2) {
        return redisTemplate.opsForSet().intersect(key1, key2);
    }

    // 获取两个集合的并集
    public Set<String> unionSets(String key1, String key2) {
        return redisTemplate.opsForSet().union(key1, key2);
    }

    // 获取两个集合的差集
    public Set<String> diffSets(String key1, String key2) {
        return redisTemplate.opsForSet().difference(key1, key2);
    }

    public Object popSet(String key){
        return redisTemplate.opsForSet().pop(key);
    }

    /*public List<Object> batchSetValue(List<String> keys, List<Object> values) {
        return redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public <K, V> Object execute(RedisOperations<K, V> operations) throws DataAccessException {
                ValueOperations<String, Object> valueOperations = (ValueOperations<String, Object>) operations.opsForValue();
                for (int i = 0; i < keys.size(); i++) {
                    valueOperations.set(keys.get(i), values.get(i));
                }
                return null;
            }
        });
    }

    public List<Object> batchSetMap(List<String> keyList, List<Map<String, Object>> mapList) {
        return redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public <K, V> Object execute(RedisOperations<K, V> operations) throws DataAccessException {
                HashOperations<String, Object, Object> hashOperations = (HashOperations<String, Object, Object>) operations.opsForHash();
                for (int i = 0; i < keyList.size(); i++) {
                    hashOperations.putAll(keyList.get(i), mapList.get(i));
                }
                return null;
            }
        });
    }

    public void saveHashesInBatch(Map<String, Map<String, Object>> batchData) {
        int batchSize = 10000;
        // 将 batchData 分批次处理
        List<Map<String, Map<String, Object>>> batches = partition(batchData, batchSize);

        for (Map<String, Map<String, Object>> batch : batches) {
            redisTemplate.executePipelined(new SessionCallback<>() {
                @Override
                public <K, V> Object execute(RedisOperations<K, V> operations) throws DataAccessException {
                    HashOperations<String, Object, Object> hashOperations = (HashOperations<String, Object, Object>) operations.opsForHash();
                    for (String key : batch.keySet()) {
                        hashOperations.putAll(key, batch.get(key));
                    }
                    return null;
                }
            });
        }
    }

    private <K, V> List<Map<K, V>> partition(Map<K, V> map, int size) {
        return Stream.iterate(0, i -> i + 1)
                .limit((map.size() + size - 1) / size)
                .map(i -> map.entrySet().stream()
                        .skip((long) i * size)
                        .limit(size)
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
                .collect(Collectors.toList());
    }

    public void saveBatch(Map<String, Object> batchData){
        redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public <K, V> Object execute(RedisOperations<K, V> operations) throws DataAccessException {
                ValueOperations<String, Object> valueOperations = (ValueOperations<String, Object>) operations.opsForValue();
                for (String key : batchData.keySet()) {
                    valueOperations.set(key, batchData.get(key));
                }
                return null;
            }
        });
    }*/

    /**
     * 批量获取 redis 存在的 key
     * @param keys
     * @return
     */
    public List<String> getValidKeys(List<String> keys) {
        List<Object> values = redisTemplate.opsForValue().multiGet(keys);

        if (values == null) {
            return new ArrayList<>();
        }

        return keys.stream()
                .filter(key -> {
                    int index = keys.indexOf(key);
                    return index < values.size() && values.get(index) != null;
                })
                .collect(Collectors.toList());
    }
}
