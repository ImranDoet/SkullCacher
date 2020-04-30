package me.imrandoet.skullcacher.api;

import org.slf4j.Logger;

import java.util.UUID;

public interface SkullCacherClient {

    void load();
    void unload();
    void setLogger(Logger logger);

    void generateTexture(UUID playerUUID, SkullCacheCallback skullCacheCallback);
    void generateTexture(String playerName, SkullCacheCallback skullCacheCallback);
    void removeTexture(UUID playerUUID, SkullCacheCallback skullCacheCallback);

}
