package com.hqu.memory;

/**
 * 启动入口
 * 分离 Main 和 Application 是为了避免 JavaFX 启动时的模块问题
 */
public class Main {
    public static void main(String[] args) {
        MemoryApp.main(args);
    }
}
