package com.cookie.lkernel.spi;

/**
 * 扩展对象存放处
 *
 * @author cookie
 * @since 2023-03-26 17:36
 */
public class Holder<T> {
    /**
     * 加载后--对象
     */
    private T obj;

    public T getObj() {
        return obj;
    }

    public void setObj(T obj) {
        this.obj = obj;
    }
}
