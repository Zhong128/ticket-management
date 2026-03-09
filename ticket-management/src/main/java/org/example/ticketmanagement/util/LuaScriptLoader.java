// org/example/ticketmanagement/util/LuaScriptLoader.java
package org.example.ticketmanagement.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class LuaScriptLoader {

    private final Map<String, DefaultRedisScript<?>> scriptCache = new HashMap<>();

    @PostConstruct
    public void init() {
        // 预加载Lua脚本
        loadScript("decrStock.lua", Long.class);
        log.info("Lua脚本加载完成");
    }

    private <T> void loadScript(String scriptName, Class<T> resultType) {
        try {
            ClassPathResource resource = new ClassPathResource("lua/" + scriptName);
            String scriptContent = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

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
