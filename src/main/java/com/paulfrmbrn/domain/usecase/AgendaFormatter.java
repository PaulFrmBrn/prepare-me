package com.paulfrmbrn.domain.usecase;

import com.paulfrmbrn.domain.model.Checklist;
import com.paulfrmbrn.domain.model.Topic;

import java.util.List;

public class AgendaFormatter {

    private AgendaFormatter() {}

    public static String format(String meetingName, List<Topic> topics) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n ").append(meetingName).append("\n");
        if (topics.isEmpty()) {
            sb.append("> No topics\n");
        } else {
            for (Topic topic : topics) {
                formatTopic(sb, topic);
            }
        }
        return sb.toString();
    }

    private static void formatTopic(StringBuilder sb, Topic topic) {
        sb.append("> ").append(topic.name()).append("\n");

        List<Checklist> checklists = topic.checklists();
        boolean singleDefaultChecklist = checklists.size() == 1 && "Checklist".equals(checklists.get(0).name());

        if (singleDefaultChecklist) {
            checklists.get(0).lastUncheckedItemName()
                    .ifPresent(item -> sb.append(item).append("\n"));
        } else {
            for (Checklist checklist : checklists) {
                checklist.lastUncheckedItemName().ifPresent(item -> {
                    sb.append(">> ").append(checklist.name()).append("\n");
                    sb.append(item).append("\n");
                });
            }
        }
    }
}
