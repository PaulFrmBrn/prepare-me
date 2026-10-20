package com.paulfrmbrn.domain.usecase;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognises a one-on-one calendar title and extracts the other person's name from it.
 *
 * <p>Titles are written by hand, so they carry noise around the two names: a ticket id
 * ("ODM-12259. Mikhail / Dmitry"), a topic prefix ("Random chat 2 - Gleb / Dmitry"), a recurring
 * marker ("1-1 Ivan / Dmitry") or a trailing subject ("Ivan / Dmitry - milestone 3"). The name
 * itself may be more than one word ("Oleg K / Dmitry").</p>
 *
 * <p>Those two demands pull against each other, and nothing in the title says which is which: a
 * prefix allowed to end at any word boundary is indistinguishable from a surname. Resolving that
 * in favour of the prefix is what once made "Oleg K / Dmitry" resolve to a person named "K". So a
 * prefix is only stripped when it ends in an explicit delimiter — a full stop, dash, colon or
 * digit followed by whitespace — and everything else before the separator is the name. A title
 * whose prefix carries no such delimiter ("Q3 planning Ivan / Dmitry") therefore resolves to the
 * wrong name or to no match at all; those need an entry in the doc mappings.</p>
 */
final class OneOnOneTitle {

    /** The user's own name, as it appears in calendar titles. */
    private static final String SELF = "(Dima|Dmitry|Dmitrii)";

    /** Leading noise, only recognised as such when it ends in a delimiter; see the class javadoc. */
    private static final String PREFIX = "(?:[^/]*?[.:\\d-]\\s+)?";

    /** One or more whitespace-separated words, allowing initials ("K."), hyphens and apostrophes. */
    private static final String NAME = "([A-Za-z][A-Za-z'-]*\\.?(?:\\s+[A-Za-z][A-Za-z'-]*\\.?)*)";

    /** Trailing subject, which must be set off from the name by a colon or whitespace. */
    private static final String SUFFIX = "(?:[:\\s].*)?";

    /**
     * Either name may come first. Group 1 holds the other person for "Name / Dmitry",
     * group 4 for "Dmitry / Name"; exactly one of the two is set on a match.
     */
    private static final Pattern PATTERN = Pattern.compile(
            "^" + PREFIX + NAME + "\\s*/\\s*" + SELF + SUFFIX + "$"
            + "|^" + PREFIX + SELF + "\\s*/\\s*" + NAME + SUFFIX + "$");

    private OneOnOneTitle() {
    }

    /** True if this title names the user and exactly one other person. */
    static boolean matches(String title) {
        return isTwoParty(title) && PATTERN.matcher(title).matches();
    }

    /**
     * The other person's name, for use as a notes document name.
     *
     * @throws IllegalArgumentException if the title is not a one-on-one; call {@link #matches}
     *                                  first when that is not already known.
     */
    static String otherPersonName(String title) {
        Matcher matcher = isTwoParty(title) ? PATTERN.matcher(title) : null;
        if (matcher == null || !matcher.matches()) {
            throw new IllegalArgumentException("Not a 1-1 title: " + title);
        }
        return matcher.group(1) != null ? matcher.group(1) : matcher.group(4);
    }

    /**
     * A second separator means a third party ("A / B / Dmitry"), which the pattern alone would
     * read as a name plus a trailing subject.
     */
    private static boolean isTwoParty(String title) {
        return title.indexOf('/') == title.lastIndexOf('/');
    }
}
