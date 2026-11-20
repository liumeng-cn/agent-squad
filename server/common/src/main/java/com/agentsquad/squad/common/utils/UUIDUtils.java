package com.agentsquad.squad.common.utils;

import java.util.UUID;

public class UUIDUtils {

    private UUIDUtils() {

    }

    public static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
