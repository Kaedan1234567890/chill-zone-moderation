package com.chillzone.moderation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PunishmentRecord {
    public UUID uuid;
    public String lastKnownName;
    public List<WarningEntry> warnings = new ArrayList<>();
    public BanEntry ban;

    public static final class WarningEntry {
        public String reason;
        public String staff;
        public long issuedAt;
    }

    public static final class BanEntry {
        public String reason;
        public String staff;
        public long issuedAt;
        public long expiresAt; // 0 = permanent
    }
}
