package nz.co.jammehcow.jenkinsdiscord.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmbedDescriptionTest {

    @Test
    void preservesFullPlasticScmChangesetId() {
        assertEquals("cs:123456", EmbedDescription.getCommitDisplayStr("cs:123456"));
    }

    @Test
    void preservesShortCommitId() {
        assertEquals("abc", EmbedDescription.getCommitDisplayStr("abc"));
    }

    @Test
    void preservesNullPlaceholder() {
        assertEquals("null  ", EmbedDescription.getCommitDisplayStr(null));
    }
}
