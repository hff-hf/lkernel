package com.cookie.lkernel.spi;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * 扩展类加载器
 *
 * @author cookie
 * @since 2023-03-26 17:40
 */
public class ExtensionLoader<T> {
    // 扩展配置文件存
    private static final String EXT_DIRECTORY = "META-INF/ext/";

    // 检验 Spi注解 value值是否合法
    private static final Pattern NAME_SEPARATOR = Pattern.compile("\\s*[,]+\\s*");

    /** 保存了内核开放的扩展点对应的 ExtensionLoader 实例对象 */
    private static final ConcurrentMap<Class<?>, ExtensionLoader<?>> EXTENSION_LOADERS = new ConcurrentHashMap<>();

    /** 保存了扩展类型 （Class） 和扩展类型的实例对象 */
    private static final ConcurrentMap<Class<?>, Object> EXTENSION_INSTANCES = new ConcurrentHashMap<>();

    /** 保存扩展的名称和实例对象 ， 扩展名称为 key  ， 扩展实例为 value*/
    private final ConcurrentMap<String, Holder<Object>> cachedInstances = new ConcurrentHashMap<>();

    /** 保存不满足装饰模式（不存在只有一个参数，并且参数是扩展点类型实例对象的构造函数）的扩展的名称*/
    private final ConcurrentMap<Class<?>, String> cachedNames = new ConcurrentHashMap<>();

    /** 保存不满足装饰模式的扩展的 Class 实例 ， 扩展的名称作为 key , Class 实例作为 value*/
    private final Holder<Map<String, Class<?>>> cachedClasses = new Holder<>();

    /** 被 @SPI 注解的 Interface ， 也就是扩展点 */
    private final Class<?> type;

    /** 同步锁 **/
    private final ReentrantLock lock = new ReentrantLock();

    /** 满足装饰模式的扩展的 Class 实例*/
    private Set<Class<?>> cachedWrapperClasses;

    /**  扩展点上 @SPI 注解指定的缺省适配扩展*/
    private String cachedDefaultName;

    /** 保存在加载扩展点配置文件时，加载扩展点过程中抛出的异常 ， key 是当前读取的扩展点配置文件的一行 ， value 是抛出的异常 */
    private Map<String, IllegalStateException> exceptions = new ConcurrentHashMap<>();

    public ExtensionLoader(Class<?> type) {
        this.type = type;
    }

    /**
     * 根据 Spi修饰的注解 获取扩展类加载器
     * @param type type
     * @return loader
     */
    public static <T> ExtensionLoader<T> getExtensionLoader(Class<T> type) {
        Objects.requireNonNull(type);
        if (!type.isInterface() || !withExtensionAnnotation(type)) {
            throw new IllegalArgumentException("Extension type is error");
        }
        return Optional.ofNullable((ExtensionLoader<T>) EXTENSION_LOADERS.get(type))
                .orElseGet(() -> {
                    EXTENSION_LOADERS.putIfAbsent(type, new ExtensionLoader<>(type));
                    return (ExtensionLoader<T>) EXTENSION_LOADERS.get(type);
                });
    }

    /**
     * 根据配置文件key获取实例对象
     * @param key key
     * @return obj
     */
    public T getExtension(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Extension name is error");
        }

        Holder<Object> holder = Optional.ofNullable(cachedInstances.get(key))
                .orElseGet(() -> {
                    cachedInstances.putIfAbsent(key, new Holder<>());
                    return cachedInstances.get(key);
                });
        Object instance = holder.getObj();
        if (Objects.nonNull(instance)) {
            return (T) instance;
        }
        try {
            lock.lock();
            instance = Optional.ofNullable(holder.getObj()).orElseGet(() -> {
                Object obj = createExtension(key);
                holder.setObj(obj);
                return obj;
            });
        } finally {
            lock.unlock();
        }
        return (T) instance;
    }

    /**
     * 创建实例对象
     * @param key key
     * @return obj
     */
    private Object createExtension(String key) {

        return null;
    }

    /**
     * 判断是否是 Spi注解
     * @param type type
     * @return res
     */
    private static <T> boolean withExtensionAnnotation(Class<T> type) {
        return type.isAnnotationPresent(Spi.class);
    }
}
