package com.example.datasheetanalyzer

import com.example.datasheetanalyzer.domain.extraction.*
import com.example.datasheetanalyzer.domain.model.*
import com.example.datasheetanalyzer.sandbox.*
import org.junit.Assert.*
import org.junit.Test

class GraphQueryTest {
    @Test fun graphTitlesAreSeparateFromNumericCriteriaAndBounded() {
        val queries=QueryNormalizer().queries(emptySet(),"recommended operating voltage","Output Power vs Supply Voltage\nGain vs Frequency\nSupply Current\nFourth plot")
        assertEquals(3,queries.count { it.mode==QueryMode.GRAPH })
        assertEquals(Criterion.SUPPLY_VOLTAGE,queries.first().criterion)
        assertEquals(QueryMode.RECOMMENDED_OPERATING,queries.first().mode)
        assertTrue(queries.filter { it.mode==QueryMode.GRAPH }.all { it.criterion==null })
    }
    @Test fun criteriaHaveStableOrderAndEveryButtonAppearsOnce() {
        val selected=Criterion.entries.toSet()
        assertEquals(selected,CriterionGroups.ordered.toSet())
        assertEquals(selected.size,CriterionGroups.ordered.size)
        assertEquals(QueryNormalizer().queries(selected,""),QueryNormalizer().queries(selected.reversed().toSet(),""))
    }
    @Test fun nativeCaptionMatcherRejectsNumericTableRows() {
        val locator=NativeFigureLocator()
        val glyphs=listOf(FigureGlyph("Output Power vs Supply Voltage",50f,100f,180f,10f),
            FigureGlyph("Supply Current 5 mA",50f,200f,180f,10f))
        val matches=locator.locate(glyphs,500f,700f,2,listOf("Output Power versus Supply Voltage","Supply Current"))
        assertEquals(1,matches.size);assertEquals(0,matches.single().requestIndex);assertEquals(2,matches.single().match.page)
        assertNotNull(matches.single().match.titleBounds)
    }
}
