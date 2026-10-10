package com.example.datasheetanalyzer

import com.example.datasheetanalyzer.domain.extraction.*
import org.junit.Assert.*
import org.junit.Test

class Stage6TableLayoutTest {
    private fun glyph(text: String,x: Float,y: Float,width: Float=20f)=PositionedGlyph(text,x,y,width)
    private fun table(max: String="12")=listOf(
        glyph("Electrical Characteristics",25f,20f,180f),
        glyph("Parameter",25f,40f,70f),glyph("Conditions",170f,40f,65f),
        glyph("Min",300f,40f),glyph("Typ",350f,40f),glyph("Max",400f,40f),glyph("Units",450f,40f,30f),
        glyph("Supply current",25f,60f,100f),glyph("VCC = 5 V",170f,60f,65f),
        glyph("8",350f,60f,5f),glyph(max,400f,60f,10f),glyph("mA",450f,60f,15f))
    @Test fun coordinatesKeepBlankMinimumAndConditions() {
        val text=PositionedTableReader().tables(table())
        assertTrue(text.contains("Supply current | VCC = 5 V |  | 8 | 12 | mA"))
        val lines=text.trim().lines()
        val result=EngineeringValueParser().parse(lines.last(),lines.dropLast(1))!!
        assertNull(result.numbers.minimum);assertEquals(8.0,result.numbers.typical!!,0.0)
        assertEquals(12.0,result.numbers.maximum!!,0.0);assertEquals("VCC = 5 V",result.conditions)
    }
    @Test fun ambiguousResultCellNotDropped() {
        assertTrue(PositionedTableReader().tables(table("?12")).isBlank())
    }
    @Test fun incompleteHeaderCannotCreateStructuredTable() {
        val withoutUnits=table().filterNot { it.text=="Units" }
        assertTrue(PositionedTableReader().tables(withoutUnits).isBlank())
    }
    @Test fun rowOrderDoesNotDependOnPdfDrawingOrder() {
        assertEquals(PositionedTableReader().tables(table()),PositionedTableReader().tables(table().reversed()))
    }
}
