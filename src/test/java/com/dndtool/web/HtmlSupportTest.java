package com.dndtool.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class HtmlSupportTest {
    @Test
    void escapesEveryHtmlTextAndAttributeDelimiterWithoutChangingUnicode() {
        assertEquals(
                "&lt;script&gt;&amp;&quot;&#39;酒馆",
                HtmlSupport.escape("<script>&\"'酒馆"));
        assertEquals("", HtmlSupport.escape(null));
    }
}
