package me.imrandoet.skullcacher.api;

import com.google.gson.*;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConfiguredSkullCacherClient implements SkullCacherClient {

    private File cacheFolder;
    private boolean ignoreErrors;

    private Map<UUID, Texture> cachedTextures; // Cache all textures with the uuid
    private ExecutorService pool = Executors.newCachedThreadPool(); // New thread pool for multithreaded requests
    private Gson gson = new GsonBuilder().setPrettyPrinting().create(); // New GSON client
    private JsonParser jsonParser = new JsonParser(); // Always have one instance of the JSON parser so we dont get issues (static methods on JsonParser throw NoSuchMethodExceptions)
    private Logger logger; // Logger that the user can set

    private static final String PROFILE_URL = "https://sessionserver.mojang.com/session/minecraft/profile/%s?unsigned=false";

    /**
     * Creates new client
     *
     * @param cacheFolder  The folder to load & save cached textures
     * @param ignoreErrors Should the client ignore textures that couldn't be loaded (this means it won't save nor notify console)
     */
    public ConfiguredSkullCacherClient(File cacheFolder, boolean ignoreErrors) {
        this.cacheFolder = cacheFolder;
        this.ignoreErrors = ignoreErrors;
        this.cachedTextures = new HashMap<>();
    }

    /**
     * Loads in cache
     */
    @Override
    public void load() {
        for (File skull : Objects.requireNonNull(cacheFolder.listFiles())) { // Get all files in the cache folder so we know what skull to cache and what not
            Texture texture = loadTextureFromJson(skull);
            if (texture == null) continue; // Something went wrong, no clue

            cachedTextures.put(texture.uuid, texture); // Add to cache
        }
    }

    /**
     * Unloads & saves cache
     */
    @Override
    public void unload() {
        for (Map.Entry<UUID, Texture> uuidTextureEntry : cachedTextures.entrySet()) { // Try to save all the cached textures
            saveTexture(uuidTextureEntry.getValue());
        }
    }

    @Override
    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    /**
     * Generates texture
     *
     * @param playerUUID         uuid
     * @param skullCacheCallback callback
     */
    @Override
    public void generateTexture(UUID playerUUID, SkullCacheCallback skullCacheCallback) {
        if (cachedTextures.containsKey(playerUUID)) { // Texture is already cached, return texture in cache!
            logger.info("[CLIENT] Texture " + playerUUID.toString() + " is already cached so we'll load it from the cache!");
            skullCacheCallback.success(cachedTextures.get(playerUUID));
            return;
        }

        Texture texture = new Texture(); // Create new texture object
        pool.execute(() -> {
            try {
                skullCacheCallback.start();
                HttpURLConnection connection = (HttpURLConnection) new URL(String.format(PROFILE_URL, playerUUID.toString().replace("-", ""))).openConnection(); //Opens connection to the given url, removes dashes in UUIDs because mojang's api doesn't like those
                connection.setReadTimeout(10000); // Timeout of 10seconds (10 000 ms)

                if (connection.getResponseCode() != 200) { // Welp, the status isn't a 200 OK so it's not a success
                    skullCacheCallback.fail();
                    return;
                }

                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));

                JsonObject jsonObject = jsonParser.parse(bufferedReader).getAsJsonObject(); // For some reason JsonParser's static parse method doesn't work..
                JsonArray properties = jsonObject.getAsJsonArray("properties");
                if (properties.size() == 0) { // Properties size is 0: properties map is empty
                    skullCacheCallback.fail();
                    return;
                }

                // Insert the texture with the values
                texture.signature = properties.get(0).getAsJsonObject().get("signature").getAsString();
                texture.value = properties.get(0).getAsJsonObject().get("value").getAsString();
                texture.uuid = playerUUID;

                // Add to cache (super super simple)
                cachedTextures.put(playerUUID, texture);

                skullCacheCallback.success(texture);
            } catch (Exception exception) {
                skullCacheCallback.error(exception);
            }
        });
    }

    /**
     * Generates texture
     *
     * @param playerName         player name
     * @param skullCacheCallback callback
     */
    @Override
    public void generateTexture(String playerName, SkullCacheCallback skullCacheCallback) {
        UUIDFetcher.getUUID(playerName, uuid -> generateTexture(uuid, skullCacheCallback));
    }

    @Override
    public void removeTexture(UUID playerUUID, SkullCacheCallback skullCacheCallback) {
        if (!cachedTextures.containsKey(playerUUID)) {
            skullCacheCallback.fail();
            return;
        }

        Texture texture = cachedTextures.get(playerUUID);
        if (removeTextureFile(texture)) {
            skullCacheCallback.success(texture);
        } else skullCacheCallback.fail();
    }


    public void saveTexture(Texture texture) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("signature", texture.signature);
        jsonObject.addProperty("value", texture.value);

        File textureFile = new File(cacheFolder, texture.uuid.toString() + ".texture");
        if (!textureFile.exists()) {
            try {
                textureFile.createNewFile();
            } catch (IOException exception) {
                if (!ignoreErrors)
                    logger.error("Couldn't create texture file " + texture.uuid + ".texture", exception);
            }
        }

        try (Writer writer = new FileWriter(textureFile)) {
            gson.toJson(jsonObject, writer);
        } catch (IOException exception) {
            if (!ignoreErrors)
                logger.error("Couldn't write to texture file " + texture.uuid + ".texture", exception);
        }
    }

    public boolean removeTextureFile(Texture texture) {
        File file = new File(cacheFolder, texture.uuid.toString() + ".yml");
        return file.delete();
    }

    public Texture loadTextureFromJson(File file) {
        try {
            JsonObject jsonObject = jsonParser.parse(new FileReader(file)).getAsJsonObject();
            Texture texture = new Texture();

            texture.signature = jsonObject.get("signature").getAsString();
            texture.value = jsonObject.get("value").getAsString();
            texture.uuid = UUID.fromString(FilenameUtils.removeExtension(file.getName()));

            return texture;
        } catch (FileNotFoundException exception) {
            if (!ignoreErrors)
                logger.error("Couldn't load texture from file " + file.getName(), exception);
        }

        return null;
    }
}
