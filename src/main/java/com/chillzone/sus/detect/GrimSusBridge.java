package com.chillzone.sus.detect;

import com.chillzone.sus.data.SusStore;

/**
 * Legacy compatibility placeholder.
 *
 * GrimAC is no longer used by Chill Zone Moderation.
 * This file intentionally contains NO Grim imports or API calls.
 * It exists only to overwrite an older GrimSusBridge.java that may still
 * be present in an existing GitHub repository after upgrading the source.
 */
@Deprecated
public final class GrimSusBridge {
    private GrimSusBridge() {}

    public static void init(Object ignoredOwner, SusStore ignoredStore) {
        // No-op. AntiFlight evidence is handled by AntiFlyEvidenceBridge.
    }
}
