package com.example.datasheetanalyzer
import com.example.datasheetanalyzer.sandbox.PdfSandboxInspector
import org.junit.Assert.*
import org.junit.Test

class PdfStructureTest {
    private fun inspect(name:String)=javaClass.getResourceAsStream("/security/$name")!!.use { PdfSandboxInspector().inspect(it) }
    @Test fun validStaticPdfPasses() { val r=inspect("valid.pdf");assertTrue(r.code,r.passed);assertEquals(1,r.pages) }
    @Test fun passwordProtectedPdfRejected() {
        val output=java.io.ByteArrayOutputStream()
        javaClass.getResourceAsStream("/security/valid.pdf")!!.use { input ->
            com.tom_roush.pdfbox.pdmodel.PDDocument.load(input).use { doc ->
                doc.protect(com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy("fixture-owner","fixture-password",
                    com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission()))
                doc.save(output)
            }
        }
        assertEquals("ENCRYPTED",PdfSandboxInspector().inspect(output.toByteArray().inputStream()).code)
    }
    @Test fun fakePdfRejected() { assertFalse(inspect("fake.pdf").passed) }
    private fun ownerProtected(extract: Boolean, active: Boolean=false): ByteArray {
        val output=java.io.ByteArrayOutputStream()
        javaClass.getResourceAsStream("/security/valid.pdf")!!.use { input ->
            com.tom_roush.pdfbox.pdmodel.PDDocument.load(input).use { doc ->
                if(active) doc.documentCatalog.cosObject.setItem(com.tom_roush.pdfbox.cos.COSName.getPDFName("JavaScript"),
                    com.tom_roush.pdfbox.cos.COSString("fixture"))
                val permission=com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission().apply { setCanExtractContent(extract) }
                doc.protect(com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy("fixture-owner","",permission))
                doc.save(output)
            }
        }
        return output.toByteArray()
    }
    @Test fun publicPdfWithOwnerPasswordAndCopyPermissionPasses() {
        val result=PdfSandboxInspector().inspect(ownerProtected(true).inputStream())
        assertTrue(result.code,result.passed)
    }
    @Test fun publicPdfWithoutCopyPermissionIsRejected() {
        assertEquals("EXTRACTION_NOT_PERMITTED",PdfSandboxInspector().inspect(ownerProtected(false).inputStream()).code)
    }
    @Test fun ownerProtectionDoesNotHideActiveContent() {
        assertEquals("ACTIVE_CONTENT",PdfSandboxInspector().inspect(ownerProtected(true,true).inputStream()).code)
    }
    @Test fun truncatedPdfRejected() {
        val bytes=javaClass.getResourceAsStream("/security/valid.pdf")!!.readBytes()
        assertFalse(PdfSandboxInspector().inspect(bytes.copyOf(bytes.size-30).inputStream()).passed)
    }
    @Test fun invalidXrefRejected() { assertFalse(inspect("bad-xref.pdf").passed) }
    @Test fun activeNamesRejected() { assertEquals("ACTIVE_CONTENT",inspect("active.pdf").code) }
    @Test fun escapedNamesRejected() { assertEquals("ACTIVE_CONTENT",inspect("escaped-active.pdf").code) }
    @Test fun compressedObjectNamesRejected() { assertEquals("ACTIVE_CONTENT",inspect("compressed-active.pdf").code) }
    @Test fun invalidPageCountRejected() { assertFalse(inspect("bad-pages.pdf").passed) }
}
