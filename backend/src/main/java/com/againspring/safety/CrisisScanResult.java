package com.againspring.safety;

import java.util.List;

public record CrisisScanResult(boolean crisis, List<String> patterns) {
    public static CrisisScanResult none() { return new CrisisScanResult(false, List.of()); }
}
