package com.example.datasheetanalyzer

import com.example.datasheetanalyzer.sandbox.*
import com.example.datasheetanalyzer.domain.document.DatasheetSection
import org.junit.Assert.*
import org.junit.Test

class EvidenceTextLocatorTest {
    private val locator=EvidenceTextLocator()
    private fun line(text: String,x: Float,y: Float,width: Float=160f)=FigureGlyph(text,x,y,width,10f)
    @Test fun findsExactTableRowWithoutHighlightingAdjacentUnrelatedCells() {
        val glyphs=listOf(line("Recommended Operating Conditions",40f,60f,280f),
            line("Supply voltage",40f,100f),line("5",250f,100f,8f),line("12",300f,100f,16f),line("20",350f,100f,16f),line("V",400f,100f,8f),
            line("Unrelated text",500f,100f,80f))
        val bounds=locator.locate(glyphs,600f,800f,"Supply voltage | 5 | 12 | 20 | V",DatasheetSection.RECOMMENDED_OPERATING).single()
        assertTrue(bounds.left<.07);assertTrue(bounds.right<.7);assertTrue(bounds.top>.1 && bounds.bottom<.15)
    }
    @Test fun sameTextInDifferentSectionsRequiresCorrectSection() {
        val glyphs=listOf(line("Absolute Maximum Ratings",40f,50f,240f),line("Supply voltage 20 V",40f,100f),
            line("Recommended Operating Conditions",40f,200f,280f),line("Supply voltage 20 V",40f,250f))
        assertTrue(locator.locate(glyphs,600f,800f,"Supply voltage 20 V").isEmpty())
        val bounds=locator.locate(glyphs,600f,800f,"Supply voltage 20 V",DatasheetSection.RECOMMENDED_OPERATING).single()
        assertTrue(bounds.top>.29)
    }
    @Test fun wrappedSourceEvidenceProducesTwoLineRectangles() {
        val glyphs=listOf(line("Recommended Operating Conditions",40f,50f,280f),line("Operating supply",40f,100f),line("voltage 5 to 20 V",40f,116f))
        val matches=locator.locate(glyphs,600f,800f,"Operating supply voltage 5 to 20 V",DatasheetSection.RECOMMENDED_OPERATING)
        assertEquals(2,matches.size)
    }
    @Test fun incompleteOrContradictoryTextCannotProduceHighlight() {
        val glyphs=listOf(line("Supply voltage 5 to 12 V",40f,100f))
        assertTrue(locator.locate(glyphs,600f,800f,"Supply voltage 5 to 20 V").isEmpty())
        assertTrue(locator.locate(glyphs,600f,800f,"").isEmpty())
    }
}
