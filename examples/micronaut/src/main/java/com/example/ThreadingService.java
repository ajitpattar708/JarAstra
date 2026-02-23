package com.example;

import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.scheduling.TaskExecutors;
import javax.inject.Singleton;
import javax.annotation.PostConstruct;

@Singleton
public class ThreadingService {

    @ExecuteOn(TaskExecutors.IO)
    public void doWork() {
        System.out.println("Doing work on IO thread");
    }

    @PostConstruct
    public void init() {
        System.out.println("Inited");
    }
}
