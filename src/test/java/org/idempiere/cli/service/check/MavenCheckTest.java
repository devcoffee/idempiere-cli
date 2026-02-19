package org.idempiere.cli.service.check;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MavenCheckTest {

    @Test
    void fixSuggestionIncludesWingetPackageForWindows() {
        MavenCheck check = new MavenCheck();
        EnvironmentCheck.FixSuggestion fix = check.getFixSuggestion("windows");

        assertEquals("Apache.Maven", fix.wingetPackage());
        assertEquals("https://maven.apache.org/download.cgi", fix.manualUrl());
    }
}

