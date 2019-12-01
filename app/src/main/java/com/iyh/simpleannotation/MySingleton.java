package com.iyh.simpleannotation;

import com.iyh.processor.Singleton;

@Singleton
public class MySingleton {

    private MySingleton() {
    }

    public static MySingleton getInstance() {
        return new MySingleton();
    }
}
