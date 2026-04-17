package com.paulfrmbrn.domain.model;

import java.util.List;

public record Topic(String id, String name, List<Checklist> checklists) {}
