package vproxy.app.app.cmd

import org.junit.Assert.assertNotNull
import org.junit.Test

class HelpCommandTest {
    @Test
    fun testHelpMarkdown() {
        assertNotNull(HelpCommand.helpMarkdown())
    }
}
