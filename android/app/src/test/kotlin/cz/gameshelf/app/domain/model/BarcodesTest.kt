package cz.gameshelf.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodesTest {

    @Test
    fun `drops the zeros that pad UPC-A to EAN-13 or GTIN-14`() {
        assertEquals("045496420055", Barcodes.normalize("0045496420055"))
        assertEquals("045496420055", Barcodes.normalize("00045496420055"))
        assertEquals("045496420055", Barcodes.normalize(" 045496420055 "))
        assertEquals("5030917077713", Barcodes.normalize("5030917077713"))
        assertEquals("96385074", Barcodes.normalize("96385074"))
    }

    @Test
    fun `compares the forms of one code as the same product`() {
        assertTrue(Barcodes.sameProduct("0045496420055", "045496420055"))
        assertFalse(Barcodes.sameProduct("5030917077713", "045496420055"))
    }

    @Test
    fun `accepts 8 to 14 digits`() {
        assertTrue(Barcodes.isValid("96385074"))
        assertTrue(Barcodes.isValid("00045496420055"))
        assertFalse(Barcodes.isValid("1234567"))
        assertFalse(Barcodes.isValid("ABC45496420055"))
    }
}
