package io.tryouts.java.primers.resiliency.circuitbreaker.service;

public interface OutOfProcessService {

    String invokeService(String request);
}
