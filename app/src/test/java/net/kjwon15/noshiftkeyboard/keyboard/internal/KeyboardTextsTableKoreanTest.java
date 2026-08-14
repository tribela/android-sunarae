// SPDX-License-Identifier: Apache-2.0
package net.kjwon15.noshiftkeyboard.keyboard.internal;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;

/**
 * Verifies that the special-character text references used in the Korean
 * jamo popup keys resolve to the expected characters.
 */
public class KeyboardTextsTableKoreanTest {

    @Test
    public void percentReferenceResolvesToPercent() {
        // !text/keyspec_symbols_percent must resolve to "%"
        final String text = KeyboardTextsTable.getText("keyspec_symbols_percent",
                KeyboardTextsTable.getTextsTable(new java.util.Locale("ko")));
        assertNotNull(text);
        assertEquals("%", text);
    }

    @Test
    public void commaReferenceResolvesToComma() {
        // !text/keyspec_comma must resolve to ","
        final String text = KeyboardTextsTable.getText("keyspec_comma",
                KeyboardTextsTable.getTextsTable(new java.util.Locale("ko")));
        assertNotNull(text);
        assertEquals(",", text);
    }

    @Test
    public void punctuationReferenceIncludesEscapedComma() {
        // !text/morekeys_punctuation must contain an escaped comma (\\,)
        final String text = KeyboardTextsTable.getText("morekeys_punctuation",
                KeyboardTextsTable.getTextsTable(new java.util.Locale("ko")));
        assertNotNull(text);
        assertTrue("expected escaped comma in punctuation popup", text.contains("\\,"));
    }

    @Test
    public void questionReferenceResolvesToQuestion() {
        // !text/keyspec_symbols_question must resolve to "?"
        final String text = KeyboardTextsTable.getText("keyspec_symbols_question",
                KeyboardTextsTable.getTextsTable(new java.util.Locale("ko")));
        assertNotNull(text);
        assertEquals("?", text);
    }

    @Test
    public void splitKeySpecsHandlesPercent() {
        // "%" (single char, not comma) -> single element array
        final String[] split = MoreKeySpec.splitKeySpecs("%");
        assertNotNull(split);
        assertEquals(1, split.length);
        assertEquals("%", split[0]);
    }

    @Test
    public void splitKeySpecsHandlesEscapedComma() {
        // "\\," -> a single comma literal (escaped from the spec separator)
        final String[] split = MoreKeySpec.splitKeySpecs("\\,");
        assertNotNull(split);
        assertEquals(1, split.length);
        assertEquals(",", KeySpecParser.getLabel(split[0]));
    }

    @Test
    public void splitKeySpecsRejectsBareCommaAsSeparator() {
        // A bare comma is the spec separator, so a single comma has no entry
        final String[] split = MoreKeySpec.splitKeySpecs(",");
        assertNull(split);
    }
}
