package com.example.myapp;

interface IUnlockrService {
    int getUid();
    String exec(String command);
    String inspectLights();
    boolean setLed(int color);
    void clearLed();
    void startRainbow();
    void stopRainbow();
}
