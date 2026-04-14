package com.paulfrmbrn.domain.model;

import java.util.List;

public record Topic(String name, List<Checklist> checklists) {}
