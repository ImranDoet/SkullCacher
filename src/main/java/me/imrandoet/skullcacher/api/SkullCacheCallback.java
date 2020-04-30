package me.imrandoet.skullcacher.api;

public interface SkullCacheCallback {

    void fail();
    void error(Exception exception);
    void start();
    void success(Texture texture);

}
