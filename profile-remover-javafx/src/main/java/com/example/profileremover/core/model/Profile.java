package com.example.profileremover.core.model;

public record Profile(
        String localPath,
        String sid,
        String userName,
        boolean special,
        boolean loaded,
        String lastUseTime,
        Long sizeBytes
) {}


