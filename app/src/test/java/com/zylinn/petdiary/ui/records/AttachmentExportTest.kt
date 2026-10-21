package com.zylinn.petdiary.ui.records

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class AttachmentExportTest {
    // P11-06: the saved copy keeps the user's name but always has the right extension
    @Test fun nameGetsTheFileExtension() {
        assertEquals("X光.jpg", AttachmentExport.nameWithExtension("X光", File("abc.jpg")))
        assertEquals("報告.PDF", AttachmentExport.nameWithExtension("報告.PDF", File("abc.pdf")))
        assertEquals("報告.pdf", AttachmentExport.nameWithExtension("報告.pdf", File("abc.pdf")))
    }
}
