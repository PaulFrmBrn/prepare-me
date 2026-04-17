package com.paulfrmbrn.domain.model;

/**
 * A topic section read back from a notes document.
 *
 * @param topicName matches the Trello topic card name
 * @param bodyText  all content beneath the topic heading (checklist item lines and
 *                  free-text notes the user wrote), excluding the heading line itself
 */
public record TopicContent(String topicName, String bodyText) {}
