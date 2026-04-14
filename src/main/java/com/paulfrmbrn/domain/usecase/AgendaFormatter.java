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
            appendNotesBlock(sb);
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

        if (checklists.isEmpty()) {
            appendNotesBlock(sb);
        } else if (singleDefaultChecklist) {
            appendChecklistContent(sb, checklists.get(0));
        } else {
            for (Checklist checklist : checklists) {
                sb.append(">> ").append(checklist.name()).append("\n");
                appendChecklistContent(sb, checklist);
            }
        }
    }

    private static void appendChecklistContent(StringBuilder sb, Checklist checklist) {
        String item = checklist.lastUncheckedItemName().orElse(null);
        sb.append(item != null ? item : "no items").append("\n");
        appendNotesBlock(sb);
    }

    private static void appendNotesBlock(StringBuilder sb) {
        sb.append("n>\n<n\n");
    }
}
