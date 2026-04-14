package com.paulfrmbrn.domain.model;

import java.util.Optional;

public record Checklist(String name, Optional<String> lastUncheckedItemName) {}
