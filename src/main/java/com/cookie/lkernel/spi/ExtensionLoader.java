package com.cookie.lkernel.spi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 扩展类加载器
 *
 * @author cookie
 * @since 2023-03-26 17:40
 */
public class ExtensionLoader<T> {
    // 扩展配置文件存
    private static final String EXT_DIRECTORY = "META-INF/ext/";

    /** 保存了内核开放的扩展点对应的 ExtensionLoader 实例对象 */
    private static final ConcurrentMap<Class<?>, ExtensionLoader<?>> EXTENSION_LOADERS = new ConcurrentHashMap<>();

    /** 保存了扩展类型 （Class） 和扩展类型的实例对象 */
    private static final ConcurrentMap<Class<?>, Object> EXTENSION_INSTANCES = new ConcurrentHashMap<>();

    /** 保存扩展的名称和实例对象 ， 扩展名称为 key  ， 扩展实例为 value*/
    private final ConcurrentMap<String, Holder<Object>> cachedInstances = new ConcurrentHashMap<>();

    /** 保存不满足装饰模式的扩展的 Class 实例 ， 扩展的名称作为 key , Class 实例作为 value*/
    private final Holder<Map<String, Class<?>>> cachedClasses = new Holder<>();

    /** 被 @SPI 注解的 Interface ， 也就是扩展点 */
    private final Class<?> type;

    /** 同步锁 **/
    private final ReentrantLock lock = new ReentrantLock();

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
            // 双重检查锁
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
     * 根据注解Spi中的value值获取实例
     * @return T
     */
    public T getExtension() {
        Spi spi = type.getAnnotation(Spi.class);
        return Optional.ofNullable(spi)
                .map(annotation -> getExtension(annotation.value()))
                .orElseThrow(() -> new NullPointerException("接口不存在 Spi 注解"));
    }

    /**
     * 创建实例对象
     * @param key key
     * @return obj
     */
    private Object createExtension(String key) {
        Class<?> clazz = getExtensionClass().get(key);
        return Optional.ofNullable(clazz).map(cls -> Optional.ofNullable(EXTENSION_INSTANCES.get(cls))
                .orElseGet(() -> {
                    try {
                        EXTENSION_INSTANCES.putIfAbsent(cls, cls.getDeclaredConstructor().newInstance());
                        return EXTENSION_INSTANCES.get(cls);
                    } catch (Exception e) {
                        throw new RuntimeException("create instance has error");
                    }
        })).orElseThrow(() -> new IllegalArgumentException("非法对象key"));
    }

    private Map<String, Class<?>> getExtensionClass() {
        Map<String, Class<?>> classMap = cachedClasses.getObj();
        return Optional.ofNullable(classMap).orElseGet(() -> {
            // 双重检查
            try {
                lock.lock();
                return Optional.ofNullable(cachedClasses.getObj()).orElseGet(this::loadExtensionCalssMap);
            }finally {
                lock.unlock();
            }
        });
    }

    private Map<String, Class<?>> loadExtensionCalssMap() {
        // 判断传进来的接口类型 type是标上Spi注解--后续做一些其他处理
        final Spi spi = type.getAnnotation(Spi.class);


        // 加载配置文件内容 构造完整文件路径 extDIr + type.name
        String file = EXT_DIRECTORY + this.type.getName();
        // 利用本类 获取classLoader
        ClassLoader classLoader = ExtensionLoader.class.getClassLoader();

        Map<String, Class<?>> cacheObjMap = new ConcurrentHashMap<>();
        try {
            Enumeration<URL> urls = classLoader == null ? ClassLoader.getSystemResources(file) : classLoader.getResources(file);
            if (Objects.nonNull(urls)) {
                while (urls.hasMoreElements()) {
                    URL resource = urls.nextElement();
                    loadResource(cacheObjMap, classLoader, resource);
                }
            }
        } catch (Exception e) {
            return Collections.emptyMap();
        }
        return cacheObjMap;
    }

    private void loadResource(Map<String, Class<?>> cacheObjMap, ClassLoader classLoader, URL resource) {
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(resource.openStream(), StandardCharsets.UTF_8))) {
            String curLine;
            while ((curLine = bufferedReader.readLine()) != null) {
                // 如果有注释， 去掉注释部分 （#）
                int indexOf = curLine.indexOf("#");
                curLine = indexOf >= 0 ? curLine.substring(0, indexOf).trim() : curLine.trim();
                if (curLine.isBlank()) {
                    continue;
                }
                String[] nameAndValue = curLine.split("=");
                if (nameAndValue.length == 2) {
                     nameAndValue[0] = nameAndValue[0].trim();
                     nameAndValue[1] = nameAndValue[1].trim();
                     if (!nameAndValue[0].isBlank() && !nameAndValue[1].isBlank()) {
                         // 加载类对象
                         loaderClass(cacheObjMap, nameAndValue[0], Class.forName(nameAndValue[1], true, classLoader));
                     }
                }
            }
            cachedClasses.setObj(cacheObjMap);
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private void loaderClass(Map<String, Class<?>> cacheObjMap, String name, Class<?> clazz) {
        // 判断type 是否可以由 clazz 转换而来; type是clazz父类
        if (!type.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException(type + "is not " + clazz.getName() + "father class");
        }
        // 如果同名只会加载第一个出现的k-v；
        cacheObjMap.putIfAbsent(name, clazz);
    }

    /**
     * 清理 加载器  & 缓存对象
     */
    public void clear() {
        if (!EXTENSION_LOADERS.isEmpty()) {
            EXTENSION_LOADERS.forEach((key, val) -> {
                clear(val);
            });
            EXTENSION_LOADERS.clear();
        }
        if (!EXTENSION_INSTANCES.isEmpty()) {
            EXTENSION_INSTANCES.clear();
        }

    }

    private void clear(ExtensionLoader<?> extensionLoader) {
        if (!extensionLoader.cachedInstances.isEmpty()) {
            cachedInstances.clear();
        }
        Map<String, Class<?>> classMap = extensionLoader.cachedClasses.getObj();
        if (Objects.nonNull(classMap) && !classMap.isEmpty()) {
            classMap.clear();
        }
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
