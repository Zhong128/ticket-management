// org/example/ticketmanagement/util/LuaScriptLoader.java
package org.example.ticketmanagement.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

@Slf4j
@Component
public class LuaScriptLoader {

    private final Map<String, DefaultRedisScript<?>> scriptCache = new HashMap<>();

    @PostConstruct
    public void init() {
        // 预加载Lua脚本
        loadScript("reserveStock.lua", List.class);
        loadScript("confirmReservation.lua", Long.class);
        loadScript("releaseReservation.lua", Long.class);
        loadScript("rebuildStockBatch.lua", Long.class);
        loadScript("activateStockEpoch.lua", Long.class);
        loadScript("retireStockEpochs.lua", Long.class);
        loadScript("applyStockProjection.lua", Long.class);
        log.info("Lua脚本加载完成");
    }

    private <T> void loadScript(String scriptName, Class<T> resultType) {
        ClassPathResource resource = new ClassPathResource("lua/" + scriptName);
        try (InputStream input = resource.getInputStream()) {
            String scriptContent = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            DefaultRedisScript<T> script = new DefaultRedisScript<>();
            script.setScriptText(scriptContent);
            script.setResultType(resultType);

            scriptCache.put(scriptName, script);
            log.info("加载Lua脚本成功: {}", scriptName);
        } catch (IOException e) {
            log.error("加载Lua脚本失败: {}", scriptName, e);
            throw new RuntimeException("Lua脚本加载失败: " + scriptName, e);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> DefaultRedisScript<T> getScript(String scriptName) {
        DefaultRedisScript<?> script = scriptCache.get(scriptName);
        if (script == null) {
            throw new IllegalArgumentException("未找到Lua脚本: " + scriptName);
        }
        return (DefaultRedisScript<T>) script;
    }
}
