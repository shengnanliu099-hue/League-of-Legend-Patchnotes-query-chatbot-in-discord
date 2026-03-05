package com.example.lolwatcher;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class GuildLiveChannelStore {
    private final Path file;
    private final ObjectMapper objectMapper;
    private final Map<Long, Long> guildToChannel;

    public GuildLiveChannelStore(Path file) {
        this.file = file;
        this.objectMapper = new ObjectMapper();
        this.guildToChannel = load();
    }

    public synchronized void setChannel(long guildId, long channelId) {
        guildToChannel.put(guildId, channelId);
        persist();
    }

    public synchronized boolean removeChannel(long guildId) {
        Long removed = guildToChannel.remove(guildId);
        if (removed != null) {
            persist();
            return true;
        }
        return false;
    }

    public synchronized Optional<Long> getChannel(long guildId) {
        return Optional.ofNullable(guildToChannel.get(guildId));
    }

    public synchronized Set<Long> allChannels() {
        return guildToChannel.values().stream().collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private Map<Long, Long> load() {
        if (!Files.exists(file)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Long> raw = objectMapper.readValue(file.toFile(), new TypeReference<>() {});
            Map<Long, Long> result = new LinkedHashMap<>();
            for (Map.Entry<String, Long> entry : raw.entrySet()) {
                result.put(Long.parseLong(entry.getKey()), entry.getValue());
            }
            return result;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load guild channel bindings from " + file, ex);
        }
    }

    private void persist() {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Map<String, Long> raw = new LinkedHashMap<>();
            for (Map.Entry<Long, Long> entry : guildToChannel.entrySet()) {
                raw.put(Long.toString(entry.getKey()), entry.getValue());
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), raw);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to persist guild channel bindings to " + file, ex);
        }
    }
}
